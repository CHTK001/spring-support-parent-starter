package com.chua.starter.docker.local;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 本机 Docker 交互终端：本地进程包裹 {@code docker exec -i}（无 TTY）。
 *
 * <p>标准输入经进程管道直写 docker CLI；stdout/stderr 由守护线程持续读取，
 * 每块按 UTF-8 解码后回调 {@code onOutput}。进程退出（含被 {@link #close()} 终止）
 * 时回调一次 {@code onClose}，语义与 SSH PTY 的 EOF 关闭对齐。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class LocalDockerProcessTerminal implements DockerTerminal {

    /**
     * docker exec 进程
     */
    private final Process process;

    /**
     * 进程标准输入
     */
    private final OutputStream stdin;

    /**
     * 输出回调
     */
    private final Consumer<String> onOutput;

    /**
     * 关闭回调（进程退出时触发一次）
     */
    private final Runnable onClose;

    /**
     * 是否已主动关闭
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 关闭回调是否已触发（保证只回调一次）
     */
    private final AtomicBoolean closeNotified = new AtomicBoolean(false);

    /**
     * 构造终端并启动读取/看护线程
     *
     * @param process  已启动的 docker exec 进程
     * @param onOutput 输出回调，可为 null
     * @param onClose  进程退出回调，可为 null
     */
    public LocalDockerProcessTerminal(Process process, Consumer<String> onOutput, Runnable onClose) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.onOutput = onOutput;
        this.onClose = onClose;
        startReader(process.getInputStream());
        startReader(process.getErrorStream());
        startWatcher();
    }

    /**
     * 启动输出排空线程（stdout 与 stderr 各一条，防管道缓冲塞死）
     *
     * @param input 进程输出流
     */
    private void startReader(InputStream input) {
        Thread thread = new Thread(() -> {
            try (InputStream in = input) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0 && onOutput != null) {
                        onOutput.accept(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                if (!closed.get()) {
                    log.debug("[docker-local] 终端输出读取结束: {}", e.getMessage());
                }
            }
        });
        thread.setDaemon(true);
        thread.setName("docker-local-terminal-" + thread.getId());
        thread.start();
    }

    /**
     * 启动进程看护线程：进程退出即触发一次关闭回调
     */
    private void startWatcher() {
        Thread thread = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            notifyClosed();
        });
        thread.setDaemon(true);
        thread.setName("docker-local-terminal-watch-" + thread.getId());
        thread.start();
    }

    /**
     * 触发关闭回调（幂等）
     */
    private void notifyClosed() {
        if (closeNotified.compareAndSet(false, true) && onClose != null) {
            try {
                onClose.run();
            } catch (Exception e) {
                log.debug("[docker-local] 终端关闭回调异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public void send(String command) throws IOException {
        sendRaw(command == null ? "" : command + "\n");
    }

    @Override
    public void sendRaw(String input) throws IOException {
        if (closed.get()) {
            throw new IOException("终端已关闭");
        }
        synchronized (stdin) {
            stdin.write(normalizeLineEndings(input).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }
    }

    /**
     * 行终止归一化：本机通道 {@code docker exec -i} 无 PTY，容器内 shell 只认 LF 作为行终止符；
     * 终端客户端按终端惯例以 CR 提交回车（Enter）或以 CRLF 结尾。真实 PTY 由行规程（icrnl）完成
     * CR→LF 转换，本机进程管道没有这层，必须在此模拟：CRLF 折叠为 LF、孤立 CR 归一为 LF。
     * 否则输入永不构成完整行，容器内命令不会执行（表现为终端敲回车无任何回显）。
     *
     * @param input 原始输入
     * @return 归一化后的输入
     */
    private String normalizeLineEndings(String input) {
        String text = input == null ? "" : input;
        if (text.indexOf('\r') < 0) {
            return text;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            stdin.close();
        } catch (IOException ignored) {
            // 输入流已关闭，无需处理
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        notifyClosed();
    }
}
