package com.chua.starter.server.support.provider.remote;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.ssh.support.client.SftpClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostHealthType;
import com.chua.starter.server.support.spi.HostMetricType;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.spi.RemoteLogChunk;
import com.chua.starter.server.support.util.SshClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * POSIX 系主机远程操作实现（SSH + SFTP）
 * <p>
 * 覆盖 Linux/macOS/FreeBSD/AIX/Solaris：这些系统的 shell 语义、路径分隔符、
 * 归档工具基本一致，故由同一实现声明多个 {@link #osTypes()}。
 * 同时作为 {@link #fallback()} 兜底实现，处理未登记的系统类型（会打告警日志）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "posix", order = 100)
public class PosixSshHostRemoteProvider implements HostRemoteProvider {

    /**
     * 覆盖的 POSIX 系系统类型
    */
    private static final List<String> OS_TYPES = List.of("LINUX", "MACOS", "FREEBSD", "AIX", "SOLARIS");

    /**
     * 命令执行默认超时（秒）：避免远端命令挂起拖死部署线程
    */
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * 健康探测最短超时（秒）
    */
    private static final int MIN_HEALTH_TIMEOUT_SECONDS = 10;

    /**
     * HTTP 探测超时上限（秒）
    */
    private static final int HTTP_PROBE_MAX_SECONDS = 10;

    /**
     * 默认 SSH 端口
    */
    private static final int DEFAULT_SSH_PORT = 22;

    /**
     * 连接超时（秒）
    */
    private static final int CONNECT_TIMEOUT_SECONDS = 8;

    /**
     * 远端临时目录
    */
    private static final String TEMP_DIR = "/tmp";

    /**
     * 路径分隔符
    */
    private static final String SEPARATOR = "/";

    /**
     * SSH 连接缓存管理器
    */
    @Autowired
    private ConnectionManager connectionManager;

    /**
     * 命令执行线程池（用于超时中断）
    */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ExecutorService execPool = Executors.newCachedThreadPool(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
        Thread t = new Thread(r, "host-remote-exec-posix");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String osType() {
        return "LINUX";
    }

    @Override
    public List<String> osTypes() {
        return OS_TYPES;
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public RemoteExecResult exec(MsServerHost host, SshCredential credential, String command, int timeoutSeconds) throws Exception {
        SshClients.Handle handle = null;
        boolean broken = false;
        try {
            handle = connectionManager.borrow(host, credential);
            final SshClients.Handle borrowed = handle;
            Future<RemoteExecResult> future = execPool.submit(() -> {
                var result = borrowed.client().exec().command(command).execute();
                return new RemoteExecResult(result.exitCode(), result.stdout(), result.stderr());
            });
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
            try {
                return future.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                future.cancel(true);
                // 超时后连接状态不可信：立即关闭，避免污染连接缓存
                borrowed.client().close();
                throw new IllegalStateException("命令执行超时(>" + timeout + "s): " + command);
            }
        } catch (Exception e) {
            broken = true;
            throw e;
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
            if (broken) {
                // 池里这条已经不可信，直接作废：否则同一主机后续命令会一直撞死连接，
                // 要等到下一次心跳巡检才被清掉
                connectionManager.invalidate(host.getId());
            }
        }
    }

    @Override
    public RemoteExecResult upload(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception {
        try (SftpClient client = buildConnectedSftp(host, credential)) {
            client.upload().local(localFile.toString()).remote(remotePath).exec();
        }
        return RemoteExecResult.ok("上传完成: " + remotePath);
    }

    @Override
    public RemoteExecResult mkdirs(MsServerHost host, SshCredential credential, String path) throws Exception {
        try (SftpClient client = buildConnectedSftp(host, credential)) {
            client.mkdir().path(path).recursive(true).exec();
        }
        return RemoteExecResult.ok("目录已就绪: " + path);
    }

    @Override
    public RemoteExecResult clearDir(MsServerHost host, SshCredential credential, String dir) throws Exception {
        return exec(host, credential, "mkdir -p " + quotePath(dir) + " && rm -rf " + quotePath(dir) + "/*", 0);
    }

    @Override
    public RemoteExecResult extract(MsServerHost host, SshCredential credential, String archivePath, String fileName, String dir) throws Exception {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String quotedDir = quotePath(dir);
        String quotedArchive = quotePath(archivePath);
        if (name.endsWith(".zip")) {
            return exec(host, credential, "mkdir -p " + quotedDir + " && unzip -o " + quotedArchive + " -d " + quotedDir, 0);
        }
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return exec(host, credential, "mkdir -p " + quotedDir + " && tar -xzf " + quotedArchive + " -C " + quotedDir, 0);
        }
        if (name.endsWith(".tar")) {
            return exec(host, credential, "mkdir -p " + quotedDir + " && tar -xf " + quotedArchive + " -C " + quotedDir, 0);
        }
        return RemoteExecResult.ok(fileName + ": 非压缩包，跳过解压");
    }

    @Override
    public RemoteExecResult remove(MsServerHost host, SshCredential credential, String path) throws Exception {
        return exec(host, credential, "rm -f " + quotePath(path), 0);
    }

    @Override
    public long fileSize(MsServerHost host, SshCredential credential, String file) {
        try {
            String output = exec(host, credential, "wc -c < " + quotePath(file) + " 2>/dev/null || echo -1", 0).stdout();
            if (output == null || output.isBlank()) {
                return -1L;
            }
            return (long) Double.parseDouble(output.trim().split("\\s+")[0]);
        } catch (Exception e) {
            log.debug("[remote] 文件大小探测失败 {}: {}", file, e.getMessage());
            return -1L;
        }
    }

    @Override
    public RemoteLogChunk tail(MsServerHost host, SshCredential credential, String file, Long cursor, int lines) throws Exception {
        long startCursor = cursor == null ? 0L : Math.max(0L, cursor);
        long size = fileSize(host, credential, file);
        // 日志轮转/截断：文件比上次记录的光标还小 → 当作新文件从头拉取，避免光标越界读不到内容
        if (size >= 0 && startCursor > size) {
            startCursor = 0L;
        }
        String command = startCursor > 0
                // 增量：tail -c +N 从第 N 字节开始读取
                ? "tail -c +" + (startCursor + 1) + " " + quotePath(file) + " 2>/dev/null"
                : "tail -n " + lines + " " + quotePath(file) + " 2>/dev/null";
        String output = exec(host, credential, command, 0).stdout();
        String content = output == null ? "" : output;
        long nextCursor = size >= 0 ? size : startCursor + content.getBytes(StandardCharsets.UTF_8).length;
        return RemoteLogChunk.of(content, nextCursor);
    }

    @Override
    public RemoteExecResult healthCheck(MsServerHost host, SshCredential credential, HostHealthType type, String target, int timeoutSeconds) throws Exception {
        int timeout = Math.max(timeoutSeconds, MIN_HEALTH_TIMEOUT_SECONDS);
        String command = switch (type == null ? HostHealthType.PORT : type) {
            case PORT -> "ss -tlnp 2>/dev/null | grep -q ':" + target + "'";
            case HTTP -> "curl -sI --max-time " + Math.min(timeout, HTTP_PROBE_MAX_SECONDS) + " http://" + target + " >/dev/null 2>&1";
            case PROCESS -> "pgrep -f '" + target + "'";
        };
        return exec(host, credential, command, timeout);
    }

    @Override
    public double metric(MsServerHost host, SshCredential credential, HostMetricType type, String target, int timeoutSeconds) throws Exception {
        int timeout = Math.max(timeoutSeconds, MIN_HEALTH_TIMEOUT_SECONDS);
        if (type == null) {
            return Double.NaN;
        }
        String key = target == null ? "" : target.trim();
        if (key.isEmpty()) {
            return Double.NaN;
        }
        if (type == HostMetricType.PORT_ALIVE && !key.chars().allMatch(Character::isDigit)) {
            // 端口号非数字时直接判为无数据：避免把任意串拼进 shell（注入风险）
            return Double.NaN;
        }
        String command = switch (type) {
            // 按命令行关键字匹配 PID 后累加 RSS（ps -o rss= 单位为 KB）换算 MB；无匹配输出 -1
            case PROCESS_MEM_MB -> "pids=$(pgrep -f " + quotePath(key) + " 2>/dev/null); "
                    + "if [ -n \"$pids\" ]; then ps -o rss= $pids | awk '{s+=$1} END {print int(s/1024)+0}'; else echo -1; fi";
            // ps -o pcpu= 为进程自启动以来的平均 CPU 占用率，与 Windows 侧口径保持一致（平均而非瞬时）
            case PROCESS_CPU -> "pids=$(pgrep -f " + quotePath(key) + " 2>/dev/null); "
                    + "if [ -n \"$pids\" ]; then ps -o pcpu= $pids | awk '{s+=$1} END {printf \"%.1f\", s}'; else echo -1; fi";
            // ss 优先、netstat 兜底：AIX/Solaris/FreeBSD 上 ss 不一定存在
            case PORT_ALIVE -> "(ss -tln 2>/dev/null || netstat -tln 2>/dev/null) | grep -q ':" + key + "' && echo 1 || echo 0";
            case HTTP_CODE -> "curl -s -o /dev/null -w '%{http_code}' --max-time "
                    + Math.min(timeout, HTTP_PROBE_MAX_SECONDS) + " http://" + key + " 2>/dev/null || echo 0";
        };
        return parseMetric(exec(host, credential, command, timeout).stdout());
    }

    /**
     * 解析指标采集输出
     * <p>
     * 取首个空白分隔的数值；输出为空、非数值或负值（-1 表示目标不存在）时返回
     * {@link Double#NaN} 表示本轮无数据。
     * </p>
     *
     * @param output 命令标准输出
     * @return 指标数值
     */
    private double parseMetric(String output) {
        if (output == null || output.isBlank()) {
            return Double.NaN;
        }
        try {
            double value = Double.parseDouble(output.trim().split("\\s+")[0]);
            return value < 0 ? Double.NaN : value;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    @Override
    public String tempDir() {
        return TEMP_DIR;
    }

    @Override
    public String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') {
                sb.append(SEPARATOR);
            }
            sb.append(part);
        }
        return sb.toString();
    }

    @Override
    public String quotePath(String path) {
        if (path == null) {
            return "''";
        }
        // POSIX 单引号包裹：内部单引号用 '\'' 断开再续，防止目录含空格/特殊字符导致命令注入
        return "'" + path.replace("'", "'\\''") + "'";
    }

    /**
     * 构建并连接 SFTP 客户端（凭据装配后立即清理私钥临时文件）
     *
     * @param host       主机
     * @param credential 凭据
     * @return 已连接的 SFTP 客户端（调用方负责关闭）
     * @throws IOException 连接失败时抛出
     */
    private SftpClient buildConnectedSftp(MsServerHost host, SshCredential credential) throws IOException {
        var builder = SftpClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() == null || host.getServerHostPort() <= 0
                        ? DEFAULT_SSH_PORT : host.getServerHostPort())
                .username(credential.username())
                .connectTimeout(CONNECT_TIMEOUT_SECONDS);
        Path tempKey = null;
        try {
            if (credential.hasKey()) {
                // 底层库仅支持路径方式加载私钥：落盘受限临时文件，装配后立即删除
                tempKey = Files.createTempFile("ms-host-remote-key-", ".pem");
                Files.writeString(tempKey, credential.privateKey());
                builder.privateKey(tempKey.toString());
                if (credential.password() != null && !credential.password().isBlank()) {
                    builder.password(credential.password());
                }
            } else {
                builder.password(credential.password() == null ? "" : credential.password());
            }
            SftpClient client = builder.build();
            client.connect();
            return client;
        } finally {
            if (tempKey != null) {
                try {
                    Files.deleteIfExists(tempKey);
                } catch (IOException ignored) {
                    // 临时私钥清理失败不影响主流程
                }
            }
        }
    }}