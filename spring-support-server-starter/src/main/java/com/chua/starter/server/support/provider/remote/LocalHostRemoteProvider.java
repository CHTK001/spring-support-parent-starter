package com.chua.starter.server.support.provider.remote;

import com.chua.starter.server.support.connection.PowerShellCommand;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostHealthType;
import com.chua.starter.server.support.spi.HostMetricType;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostShellDialect;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.spi.RemoteLogChunk;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 本机远程操作实现（本地进程 + 本地文件系统）
 * <p>
 * 服务端与 Agent 同机时的直装通道：{@code AgentDeployServiceImpl} 判定目标地址指向本机
 * （回环或本机网卡地址）后直接实例化本实现，跳过 SSH/WinRM 凭据解析——命令经本地进程执行，
 * 文件经本地文件系统拷贝，语义与远程通道完全一致。
 * </p>
 * <p>
 * <b>不注册进 SPI 路由</b>：本实现不标注 {@code @Spi}、不登记 {@code META-INF/extensions}，
 * 且 {@link #osTypes()} 返回空列表，避免注册中心把它当作远程通道，劫持同平台远程主机的路由。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LocalHostRemoteProvider implements HostRemoteProvider {

    /**
     * 当前运行平台是否 Windows
     */
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");

    /**
     * 路径分隔符（与当前平台一致）
     */
    private static final String SEPARATOR = WINDOWS ? "\\" : "/";

    /**
     * 命令执行默认超时（秒）
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
     * 本地 PowerShell 输出编码前言：输出被重定向到管道时控制台代码页可能不可设，
     * 失败静默忽略（与远端 chcp 65001 前缀同口径，尽力让输出落到 UTF-8）
     */
    private static final String LOCAL_UTF8_PROLOGUE =
            "try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}\r\n";

    @Override
    public String osType() {
        return localOsType();
    }

    @Override
    public List<String> osTypes() {
        // 刻意为空：本实现只由部署流程直接实例化，不参与按系统类型的 SPI 路由
        return List.of();
    }

    @Override
    public HostShellDialect shellDialect() {
        return WINDOWS ? HostShellDialect.POWERSHELL : HostShellDialect.POSIX;
    }

    @Override
    public RemoteExecResult exec(MsServerHost host, SshCredential credential, String command, int timeoutSeconds) throws Exception {
        int timeout = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        if (WINDOWS) {
            return execPowerShell(command, timeout);
        }
        return execNative(new String[]{"/bin/sh", "-c", command}, timeout);
    }

    @Override
    public RemoteExecResult upload(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception {
        Path target = Paths.get(remotePath);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(localFile, target, StandardCopyOption.REPLACE_EXISTING);
        return RemoteExecResult.ok("上传完成: " + remotePath + " (" + Files.size(localFile) + " 字节)");
    }

    @Override
    public RemoteExecResult mkdirs(MsServerHost host, SshCredential credential, String path) throws Exception {
        Files.createDirectories(Paths.get(path));
        return RemoteExecResult.ok();
    }

    @Override
    public RemoteExecResult clearDir(MsServerHost host, SshCredential credential, String dir) throws Exception {
        Path directory = Paths.get(dir);
        if (Files.isDirectory(directory)) {
            try (Stream<Path> children = Files.list(directory)) {
                children.sorted(Comparator.reverseOrder()).forEach(this::deleteRecursively);
            }
        }
        Files.createDirectories(directory);
        return RemoteExecResult.ok();
    }

    @Override
    public RemoteExecResult extract(MsServerHost host, SshCredential credential, String archivePath, String fileName, String dir) throws Exception {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        Path target = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(target);
        if (name.endsWith(".zip")) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(archivePath)), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path out = target.resolve(entry.getName()).normalize();
                    if (!out.startsWith(target)) {
                        // 防 zip-slip：条目路径越出目标目录时跳过
                        zis.closeEntry();
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            return RemoteExecResult.ok("解压完成: " + dir);
        }
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".tar")) {
            // Windows 10 1803+ 与主流 Linux 均自带 bsdtar/tar
            return exec(host, credential, "tar -xf " + quotePath(archivePath) + " -C " + quotePath(dir), 0);
        }
        return RemoteExecResult.ok(fileName + ": 非压缩包，跳过解压");
    }

    @Override
    public RemoteExecResult remove(MsServerHost host, SshCredential credential, String path) throws Exception {
        deleteRecursively(Paths.get(path));
        return RemoteExecResult.ok();
    }

    @Override
    public long fileSize(MsServerHost host, SshCredential credential, String file) {
        try {
            return Files.size(Paths.get(file));
        } catch (Exception e) {
            log.debug("[local-remote] 文件大小探测失败 {}: {}", file, e.getMessage());
            return -1L;
        }
    }

    @Override
    public RemoteLogChunk tail(MsServerHost host, SshCredential credential, String file, Long cursor, int lines) throws Exception {
        if (file == null || file.isBlank()) {
            return RemoteLogChunk.of("", 0L);
        }
        Path path = Paths.get(file);
        if (!Files.isRegularFile(path)) {
            return RemoteLogChunk.of("", 0L);
        }
        long size = Files.size(path);
        long start = cursor == null ? 0L : Math.max(0L, cursor);
        if (start > size) {
            // 日志轮转/截断：文件比上次记录的光标还小 → 当作新文件从头拉取
            start = 0L;
        }
        if (start > 0) {
            return RemoteLogChunk.of(readFrom(path, start), size);
        }
        return RemoteLogChunk.of(readTailLines(path, Math.max(1, lines)), size);
    }

    @Override
    public RemoteExecResult healthCheck(MsServerHost host, SshCredential credential, HostHealthType type, String target, int timeoutSeconds) throws Exception {
        int timeout = Math.max(timeoutSeconds, MIN_HEALTH_TIMEOUT_SECONDS);
        HostHealthType healthType = type == null ? HostHealthType.PORT : type;
        return switch (healthType) {
            case PORT -> portProbe(host, target, timeout)
                    ? RemoteExecResult.ok() : new RemoteExecResult(1, "", "端口未监听: " + target);
            case HTTP -> httpCode(host, target, timeout) >= 200 && httpCode(host, target, timeout) < 400
                    ? RemoteExecResult.ok() : new RemoteExecResult(1, "", "HTTP 探测失败: " + target);
            case PROCESS -> exec(host, credential, processExistsScript(target), timeout);
            default -> throw new IllegalStateException("未预期的取值: " + type);
        };
    }

    @Override
    public double metric(MsServerHost host, SshCredential credential, HostMetricType type, String target, int timeoutSeconds) throws Exception {
        int timeout = Math.max(timeoutSeconds, MIN_HEALTH_TIMEOUT_SECONDS);
        if (type == null || target == null || target.isBlank()) {
            return Double.NaN;
        }
        String key = target.trim();
        return switch (type) {
            case PORT_ALIVE -> !key.chars().allMatch(Character::isDigit)
                    ? Double.NaN : (portProbe(host, key, timeout) ? 1D : 0D);
            case HTTP_CODE -> httpCode(host, key, timeout);
            case PROCESS_MEM_MB, PROCESS_CPU -> {
                RemoteExecResult result = exec(host, credential, processMetricScript(type, key), timeout);
                if (!result.success()) {
                    throw new IllegalStateException("指标采集失败(" + type.desc() + "): " + result.failureMessage());
                }
                yield parseMetric(result.stdout());
            }
            default -> throw new IllegalStateException("未预期的取值: " + type);
        };
    }

    @Override
    public String tempDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        return tmp == null || tmp.isBlank() ? (WINDOWS ? "C:\\Windows\\Temp" : "/tmp") : tmp;
    }

    @Override
    public String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\\' && sb.charAt(sb.length() - 1) != '/') {
                sb.append(SEPARATOR);
            }
            sb.append(part);
        }
        return sb.toString();
    }

    @Override
    public String quotePath(String path) {
        if (WINDOWS) {
            return "'" + psEscape(path) + "'";
        }
        return "'" + (path == null ? "" : path).replace("'", "'\\''") + "'";
    }

    /**
     * 本机执行 PowerShell 脚本：优先 PowerShell 7，不可用时回退 Windows PowerShell 5.1。
     * <p>脚本按 UTF-16LE + Base64 经 {@code -EncodedCommand} 下发，规避引号/换行转义问题；
     * 本地 ProcessBuilder 直传参数，不经 cmd 包装，因此没有远端 8KB 命令行长度限制。</p>
     *
     * @param script         PowerShell 脚本
     * @param timeoutSeconds 超时秒
     * @return 执行结果
     * @throws Exception 执行失败或超时
     */
    private RemoteExecResult execPowerShell(String script, int timeoutSeconds) throws Exception {
        String ps = PowerShellCommand.body(LOCAL_UTF8_PROLOGUE + (script == null ? "" : script));
        String encoded = Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
        try {
            return execNative(new String[]{PowerShellCommand.MODERN_SHELL, "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-OutputFormat", "Text", "-EncodedCommand", encoded}, timeoutSeconds);
        } catch (IOException e) {
            // pwsh 未安装：回退系统自带的 Windows PowerShell 5.1
            return execNative(new String[]{PowerShellCommand.LEGACY_SHELL, "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-OutputFormat", "Text", "-EncodedCommand", encoded}, timeoutSeconds);
        }
    }

    /**
     * 启动本地进程并收集输出。
     * <p>两条输出流各由守护线程排空，避免子进程写满管道缓冲区后与主线程互等死锁。</p>
     *
     * @param command        命令与参数
     * @param timeoutSeconds 超时秒
     * @return 执行结果
     * @throws Exception 启动失败或执行超时
     */
    private RemoteExecResult execNative(String[] command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command).start();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outDrain = drain(process.getInputStream(), stdout);
        Thread errDrain = drain(process.getErrorStream(), stderr);
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("本机命令被中断: " + command[0], e);
        }
        if (!finished) {
            process.destroyForcibly();
            outDrain.join(1000L);
            errDrain.join(1000L);
            throw new IllegalStateException("本机命令执行超时(" + timeoutSeconds + "s): " + command[0]);
        }
        outDrain.join(TimeUnit.SECONDS.toMillis(5));
        errDrain.join(TimeUnit.SECONDS.toMillis(5));
        return new RemoteExecResult(process.exitValue(), stdout.toString(), stderr.toString());
    }

    /**
     * 后台排空进程输出流。
     *
     * @param input  进程输出流
     * @param target 收集缓冲
     * @return 排空线程
     */
    private Thread drain(InputStream input, StringBuilder target) {
        Thread thread = new Thread(() -> {
            try (InputStream in = input) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    target.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                log.debug("[local-remote] 读取进程输出失败: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 递归删除文件或目录（不存在视为成功，删除失败仅记录调试日志）。
     *
     * @param path 目标路径
     */
    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> children = Files.list(path)) {
                    children.forEach(this::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("[local-remote] 删除失败 {}: {}", path, e.getMessage());
        }
    }

    /**
     * 从指定字节偏移读取文件至末尾（实时日志增量帧）。
     *
     * @param path  文件路径
     * @param start 起始字节偏移
     * @return 文本内容
     * @throws IOException 读取失败
     */
    private String readFrom(Path path, long start) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = raf.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 读取文件末尾若干行（实时日志首帧）。
     *
     * @param path  文件路径
     * @param lines 行数
     * @return 文本内容
     * @throws IOException 读取失败
     */
    private String readTailLines(Path path, int lines) throws IOException {
        byte[] all = Files.readAllBytes(path);
        int newlines = 0;
        int cut = 0;
        for (int i = all.length - 1; i >= 0; i--) {
            if (all[i] == '\n' && ++newlines > lines) {
                cut = i + 1;
                break;
            }
        }
        return new String(all, cut, all.length - cut, StandardCharsets.UTF_8);
    }

    /**
     * 端口监听探测：优先按主机地址连接，未配置时连回环地址。
     *
     * @param host           主机
     * @param target         端口号
     * @param timeoutSeconds 超时秒
     * @return 可连通返回 true
     */
    private boolean portProbe(MsServerHost host, String target, int timeoutSeconds) {
        int port;
        try {
            port = Integer.parseInt(target == null ? "" : target.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        String address = host == null || host.getServerHostAddress() == null || host.getServerHostAddress().isBlank()
                ? "127.0.0.1" : host.getServerHostAddress().trim();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port),
                    (int) TimeUnit.SECONDS.toMillis(Math.min(timeoutSeconds, HTTP_PROBE_MAX_SECONDS)));
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * HTTP 探测并返回状态码（target 为 host:port 或完整 URL，探测失败返回 0）。
     *
     * @param host           主机
     * @param target         探测目标
     * @param timeoutSeconds 超时秒
     * @return HTTP 状态码
     */
    private double httpCode(MsServerHost host, String target, int timeoutSeconds) {
        if (target == null || target.isBlank()) {
            return 0D;
        }
        String url = target.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            int timeoutMs = (int) TimeUnit.SECONDS.toMillis(Math.min(timeoutSeconds, HTTP_PROBE_MAX_SECONDS));
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestMethod("GET");
            return connection.getResponseCode();
        } catch (Exception e) {
            return 0D;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 进程存在性探测脚本（按当前平台方言生成）。
     *
     * @param target 进程名或命令行关键字
     * @return 可执行探测命令
     */
    private String processExistsScript(String target) {
        if (WINDOWS) {
            return "if (Get-Process -Name '" + psEscape(target)
                    + "' -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }";
        }
        return "pgrep -f " + quotePath(target) + " >/dev/null 2>&1";
    }

    /**
     * 进程指标采集脚本（按当前平台方言生成）。
     * <p>Windows 与 {@link WindowsHostRemoteProvider} 同口径；POSIX 为兜底口径
     * （本实现不经注册中心路由，POSIX 指标实际不会走这里）。</p>
     *
     * @param type 指标类型
     * @param key  进程关键字
     * @return 采集命令
     */
    private String processMetricScript(HostMetricType type, String key) {
        if (WINDOWS) {
            if (type == HostMetricType.PROCESS_MEM_MB) {
                return "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '"
                        + psEscape(key) + "' }; "
                        + "if ($p) { [int](($p | Measure-Object -Property WorkingSetSize -Sum).Sum / 1MB) } else { -1 }";
            }
            return "$p = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '"
                    + psEscape(key) + "' }; "
                    + "if ($p) { $cpu = ($p | Measure-Object -Property CPU -Sum).Sum; "
                    + "$st = ($p | Sort-Object StartTime | Select-Object -First 1).StartTime; "
                    + "$sec = ((Get-Date) - $st).TotalSeconds; "
                    + "if ($sec -gt 0) { [math]::Round($cpu / $sec * 100, 1) } else { -1 } } else { -1 }";
        }
        if (type == HostMetricType.PROCESS_MEM_MB) {
            return "ps -eo rss=,args= 2>/dev/null | grep -- " + quotePath(key)
                    + " | grep -v grep | awk '{s+=$1} END {if (s>0) printf \"%d\", s/1024; else print -1}'";
        }
        return "ps -eo pcpu=,args= 2>/dev/null | grep -- " + quotePath(key)
                + " | grep -v grep | awk '{s+=$1} END {if (NR>0) printf \"%.1f\", s; else print -1}'";
    }

    /**
     * 解析指标采集输出：取首个空白分隔的数值；为空、非数值或负值（-1 表示目标不存在）时
     * 返回 {@link Double#NaN} 表示本轮无数据。
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

    /**
     * 当前运行平台的系统类型编码。
     *
     * @return WINDOWS / MACOS / LINUX
     */
    private String localOsType() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "WINDOWS";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "MACOS";
        }
        return "LINUX";
    }

    /**
     * PowerShell 单引号字面量转义（内部单引号翻倍）。
     *
     * @param value 原始文本
     * @return 可安全嵌入单引号字面量的文本
     */
    private String psEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }
}
