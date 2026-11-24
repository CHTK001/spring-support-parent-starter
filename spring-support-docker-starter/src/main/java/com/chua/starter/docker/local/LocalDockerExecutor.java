package com.chua.starter.docker.local;

import com.chua.starter.server.support.spi.RemoteExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 本机 Docker 命令执行器：本机进程直启 docker CLI，不经 SSH/WinRM。
 *
 * <p>本模块的业务命令全部形如 {@code docker ...}，由本服务自行拼装
 * （参数经单引号转义或白名单校验），因此按 shell 词法切分后经
 * {@link ProcessBuilder} 直启 docker CLI：POSIX 与 Windows 同一套路径，
 * 无需外壳包装，不受命令行长度限制与控制台编码转义影响。
 * 重定向（{@code >/dev/null}、{@code 2>&1} 等）不交外壳，改为进程级处理：
 * {@code 2>&1} 用 {@code redirectErrorStream} 合并，其余重定向直接丢弃。</p>
 *
 * <p>不注册进远程通道 SPI：本执行器只服务于「主机即本机」的 Docker 通道，
 * 与 {@code LocalHostRemoteProvider} 同一原则。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
public class LocalDockerExecutor {

    /**
     * 一次性命令默认超时（秒）
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 构造执行器
     */
    public LocalDockerExecutor() {
    }

    /**
     * 执行本机 docker 命令并返回结构化结果
     *
     * @param command        命令（docker CLI 形态，可含重定向）
     * @param timeoutSeconds 超时（秒）
     * @param onLine         行输出回调，可为 null
     * @return 退出码 + 标准输出 + 错误输出
     * @throws Exception 启动失败、执行中断或超时
     */
    public RemoteExecResult exec(String command, int timeoutSeconds, Consumer<String> onLine) throws Exception {
        ParsedCommand parsed = parse(command);
        ProcessBuilder builder = new ProcessBuilder(parsed.args());
        if (parsed.mergeStderr()) {
            builder.redirectErrorStream(true);
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            // docker CLI 不存在等启动失败：按 127（command not found）口径返回，便于上层统一提示
            return new RemoteExecResult(127, "", "docker: command not found (" + e.getMessage() + ")");
        }
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = readLines(process.getInputStream(), line -> {
            if (onLine != null) {
                onLine.accept(line);
            }
            synchronized (stdout) {
                stdout.append(line).append('\n');
            }
        });
        Thread errReader = parsed.mergeStderr() ? null : drain(process.getErrorStream(), stderr);
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("本机命令被中断: " + parsed.args().get(0), e);
        }
        if (!finished) {
            process.destroyForcibly();
            outReader.join(1000L);
            if (errReader != null) {
                errReader.join(1000L);
            }
            throw new IllegalStateException("本机命令执行超时(" + timeoutSeconds + "s): "
                    + String.join(" ", parsed.args()));
        }
        outReader.join(TimeUnit.SECONDS.toMillis(5));
        if (errReader != null) {
            errReader.join(TimeUnit.SECONDS.toMillis(5));
        }
        return new RemoteExecResult(process.exitValue(), stdout.toString(), stderr.toString());
    }

    /**
     * 跟随式执行（日志流 / 拉取 / 运行容器等长任务）：不设总超时，
     * 输出实时回调；返回句柄用于等待退出码或中途终止。
     *
     * @param command 命令（docker CLI 形态，可含重定向）
     * @param onLine  行输出回调
     * @return 跟随句柄
     * @throws Exception 进程启动失败
     */
    public FollowHandle follow(String command, Consumer<String> onLine) throws Exception {
        ParsedCommand parsed = parse(command);
        ProcessBuilder builder = new ProcessBuilder(parsed.args());
        if (parsed.mergeStderr()) {
            builder.redirectErrorStream(true);
        }
        Process process = builder.start();
        Thread outReader = readLines(process.getInputStream(), onLine);
        if (!parsed.mergeStderr()) {
            // stderr 未合并时也要排空，避免管道缓冲塞死导致进程卡住
            drain(process.getErrorStream(), new StringBuilder());
        }
        return new FollowHandle(process, outReader);
    }

    /**
     * 打开本机容器交互终端：{@code docker exec -i <id> <shell>}（无 TTY）
     *
     * @param containerId 容器标识（已白名单校验）
     * @param shell       容器内 shell（如 {@code /bin/sh}，可含参数）
     * @param onOutput    输出回调，可为 null
     * @param onClose     进程退出回调，可为 null
     * @return 交互终端
     * @throws Exception 进程启动失败
     */
    public DockerTerminal openTerminal(String containerId, String shell,
                                        Consumer<String> onOutput, Runnable onClose) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("exec");
        args.add("-i");
        args.add(containerId);
        for (String part : (shell == null ? "" : shell).trim().split("\\s+")) {
            if (!part.isBlank()) {
                args.add(part);
            }
        }
        Process process = new ProcessBuilder(args).start();
        return new LocalDockerProcessTerminal(process, onOutput, onClose);
    }

    /**
     * 本机 {@code docker login}：密码经标准输入喂给 {@code --password-stdin}，
     * 不出现在命令行参数，也不进入任何 shell 历史。
     *
     * @param username 用户名
     * @param host     镜像库地址
     * @param password 密码（明文，仅经 stdin 传递）
     * @param onLine   输出行回调，可为 null
     * @return 退出码 + 标准输出 + 错误输出
     * @throws Exception 启动失败、中断或超时
     */
    public RemoteExecResult login(String username, String host, String password,
                                  Consumer<String> onLine) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder("docker", "login", "-u", username,
                    "--password-stdin", host).start();
        } catch (IOException e) {
            return new RemoteExecResult(127, "", "docker: command not found (" + e.getMessage() + ")");
        }
        String safe = password == null ? "" : password.replace("\r", "").replace("\n", "");
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write((safe + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = readLines(process.getInputStream(), line -> {
            synchronized (stdout) {
                stdout.append(line).append('\n');
            }
            if (onLine != null) {
                onLine.accept(line);
            }
        });
        Thread errReader = drain(process.getErrorStream(), stderr);
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            outReader.join(1000L);
            errReader.join(1000L);
            throw new IllegalStateException("本机 docker login 执行超时");
        }
        outReader.join(TimeUnit.SECONDS.toMillis(5));
        errReader.join(TimeUnit.SECONDS.toMillis(5));
        return new RemoteExecResult(process.exitValue(), stdout.toString(), stderr.toString());
    }

    /**
     * 按行读取并回调，同时累计进缓冲
     *
     * @param input  输入流
     * @param onLine 行回调
     * @return 读取线程
     */
    private Thread readLines(InputStream input, Consumer<String> onLine) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8), 8192)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (onLine != null) {
                        onLine.accept(line);
                    }
                }
            } catch (IOException e) {
                log.debug("[docker-local] 读取进程输出结束: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.setName("docker-local-exec-" + thread.getId());
        thread.start();
        return thread;
    }

    /**
     * 后台排空流并累计（UTF-8）
     *
     * @param input  输入流
     * @param target 累计缓冲
     * @return 排空线程
     */
    private Thread drain(InputStream input, StringBuilder target) {
        Thread thread = new Thread(() -> {
            try (InputStream in = input) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    synchronized (target) {
                        target.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                log.debug("[docker-local] 排空进程输出结束: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.setName("docker-local-drain-" + thread.getId());
        thread.start();
        return thread;
    }

    /**
     * 解析 docker 命令：按 shell 词法切分（单引号含 {@code '\''} 转义、双引号），
     * 剥离重定向；遇到 {@code ;} {@code |} {@code &&} {@code ||} 即截断
     * （本模块命令均为单段 docker 命令，这些操作符不出现，截断仅为兜底）。
     *
     * @param command 原始命令
     * @return 参数列表 + 是否合并 stderr
     */
    private ParsedCommand parse(String command) {
        String raw = command == null ? "" : command.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("本机 Docker 命令不能为空");
        }
        List<String> args = new ArrayList<>();
        boolean mergeStderr = false;
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (isCommandSeparator(raw, i)) {
                // 多段命令只执行首段，其余丢弃
                break;
            }
            StringBuilder token = new StringBuilder();
            boolean inSingle = false;
            boolean inDouble = false;
            while (i < n) {
                char ch = raw.charAt(i);
                if (inSingle) {
                    if (ch == '\'') {
                        // '\'' 转义（close + \' + reopen）：还原为一个单引号字面量
                        if (i + 3 < n && raw.charAt(i + 1) == '\\' && raw.charAt(i + 2) == '\''
                                && raw.charAt(i + 3) == '\'') {
                            token.append('\'');
                            i += 4;
                            continue;
                        }
                        inSingle = false;
                        i++;
                        continue;
                    }
                    token.append(ch);
                    i++;
                    continue;
                }
                if (inDouble) {
                    if (ch == '"') {
                        inDouble = false;
                        i++;
                        continue;
                    }
                    if (ch == '\\' && i + 1 < n) {
                        char next = raw.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            token.append(next);
                            i += 2;
                            continue;
                        }
                    }
                    token.append(ch);
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    inSingle = true;
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inDouble = true;
                    i++;
                    continue;
                }
                if (Character.isWhitespace(ch) || isCommandSeparator(raw, i)) {
                    break;
                }
                token.append(ch);
                i++;
            }
            String value = token.toString();
            if ("2>&1".equals(value)) {
                mergeStderr = true;
                continue;
            }
            if (value.matches("^\\d*[>&].*")) {
                // >/dev/null、2>/dev/null、>>file、&>file 等：丢弃（stderr/stdout 由进程管道接管）
                continue;
            }
            if (!value.isEmpty()) {
                args.add(value);
            }
        }
        if (args.isEmpty()) {
            throw new IllegalArgumentException("本机 Docker 命令解析结果为空: " + raw);
        }
        return new ParsedCommand(List.copyOf(args), mergeStderr);
    }

    /**
     * 是否顶层命令分隔符（; | && ||）
     *
     * @param raw    原始命令
     * @param index  当前位置
     * @return 是否分隔符
     */
    private boolean isCommandSeparator(String raw, int index) {
        char c = raw.charAt(index);
        if (c == ';' || c == '|') {
            return true;
        }
        return c == '&' && index + 1 < raw.length() && raw.charAt(index + 1) == '&';
    }

    /**
     * 解析结果
     *
     * @param args        docker CLI 参数
     * @param mergeStderr 是否已用 redirectErrorStream 合并 stderr
     */
    private record ParsedCommand(List<String> args, boolean mergeStderr) {
    }

    /**
     * 跟随式执行句柄：等待退出码或中途终止
     */
    public static final class FollowHandle implements AutoCloseable {

        /**
         * 执行进程
         */
        private final Process process;

        /**
         * 输出读取线程
         */
        private final Thread reader;

        /**
         * 构造句柄
         *
         * @param process 执行进程
         * @param reader  输出读取线程
         */
        FollowHandle(Process process, Thread reader) {
            this.process = process;
            this.reader = reader;
        }

        /**
         * 等待进程退出并返回退出码（在异步线程中调用）
         *
         * @return 退出码
         */
        public int await() {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                reader.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return process.exitValue();
        }

        /**
         * 终止进程（输出流随之关闭，读取线程自然结束）
         */
        @Override
        public void close() {
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        }
    }
}
