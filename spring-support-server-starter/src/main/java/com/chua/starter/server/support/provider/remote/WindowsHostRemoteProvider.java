package com.chua.starter.server.support.provider.remote;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostHealthType;
import com.chua.starter.server.support.spi.HostMetricType;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostShellDialect;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.spi.RemoteLogChunk;
import com.chua.winrm.support.client.WinRmExecClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

/**
 * Windows 主机远程操作实现（WinRM + PowerShell）
 * <p>
 * 为什么必须独立实现而不是复用 SSH：Windows 默认不提供 SSH 服务，
 * 把 cmd/PowerShell 命令塞进 SSH 通道在真实 Windows 主机上根本连不上。
 * 本实现走 WinRM（Windows 远程管理，默认端口 5985/HTTP），
 * 所有命令以 PowerShell 脚本经 {@code -EncodedCommand}（UTF-16LE + Base64）下发，
 * 从根本上规避多行脚本与引号的转义问题。
 * </p>
 *
 * <p>
 * 文件传输说明：WinRM 协议本身不提供文件传输能力，上传只能以「Base64 分块 + PowerShell 追加写入」
 * 实现，并受单条命令行长上限约束，因此仅适用于配置/脚本这类小文件；
 * 大文件（如 agent 包）必须由目标机经 HTTP 自行拉取，超过阈值本实现直接快速失败。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class WindowsHostRemoteProvider implements HostRemoteProvider {

    /**
     * 默认 WinRM 端口（HTTP）
    */
    private static final int DEFAULT_WINRM_PORT = 5985;

    /**
     * 命令执行默认超时（秒）
    */
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * WinRM 连接超时（毫秒）
    */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /**
     * 健康探测最短超时（秒）
    */
    private static final int MIN_HEALTH_TIMEOUT_SECONDS = 10;

    /**
     * HTTP 探测超时上限（秒）
    */
    private static final int HTTP_PROBE_MAX_SECONDS = 10;

    /**
     * 单次上传分块字节数
     * <p>
     * 瓶颈不是 WinRM 报文额度，而是最终落到远端 cmd.exe 的命令行长（上限 8191 字符）：
     * 脚本本身还要经 {@code -EncodedCommand} 再 Base64 一次，命令行按原始字节约 2.67 倍膨胀。
     * 实测 6KB 块报「The command line is too long.」、12KB 块直接 SoapFault，故取 1536 字节。
     * </p>
     */
    private static final int UPLOAD_CHUNK_BYTES = 1536;

    /**
     * 分块上传可接受的文件大小上限（约 170 次往返）
     * <p>
     * 超过这个规模的文件必须由目标机自行 HTTP 拉取，不能靠命令行搬运。
     * </p>
     */
    private static final long MAX_UPLOAD_BYTES = 256 * 1024L;

    /**
     * 远端临时目录
    */
    private static final String TEMP_DIR = "C:\\Windows\\Temp";

    /**
     * 路径分隔符
    */
    private static final String SEPARATOR = "\\";

    @Override
    public String osType() {
        return "WINDOWS";
    }

    @Override
    public boolean fallback() {
        return false;
    }

    @Override
    public HostShellDialect shellDialect() {
        // WinRM 通道下发的是 PowerShell 脚本（-EncodedCommand），AI 生成脚本与高危审计都须按 PS 语法
        return HostShellDialect.POWERSHELL;
    }

    @Override
    public RemoteExecResult exec(MsServerHost host, SshCredential credential, String command, int timeoutSeconds) throws Exception {
        try (WinRmExecClient client = open(host, credential, timeoutSeconds)) {
            return execOn(client, command);
        }
    }

    @Override
    public RemoteExecResult upload(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception {
        long localSize = Files.size(localFile);
        if (localSize > MAX_UPLOAD_BYTES) {
            // 上千次命令行往返不是「慢」而是必然超时，直接给出可执行的处置方式
            throw new IllegalStateException("WinRM 通道不支持传输 " + localSize + " 字节的文件（上限 "
                    + MAX_UPLOAD_BYTES + " 字节），请配置 monitor.agent.jar-url 让目标机自行下载");
        }
        try (WinRmExecClient client = open(host, credential, 0)) {
            String parent = parentOf(remotePath);
            if (parent != null) {
                RemoteExecResult mk = mkdirsOn(client, parent);
                if (!mk.success()) {
                    throw new IllegalStateException("创建远端目录失败: " + mk.failureMessage());
                }
            }
            // 以空内容创建/截断目标文件，后续分块统一走追加模式
            RemoteExecResult truncate = execOn(client,
                    "[IO.File]::WriteAllBytes('" + psEscape(remotePath) + "', (New-Object byte[] 0))");
            if (!truncate.success()) {
                throw new IllegalStateException("初始化远端文件失败: " + truncate.failureMessage());
            }
            byte[] buffer = new byte[UPLOAD_CHUNK_BYTES];
            try (InputStream in = Files.newInputStream(localFile)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    byte[] chunk = read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
                    String script = "$p='" + psEscape(remotePath) + "';"
                            + "$b=[Convert]::FromBase64String('" + Base64.getEncoder().encodeToString(chunk) + "');"
                            + "$fs=[IO.File]::Open($p,'Append','Write');"
                            + "try { $fs.Write($b,0,$b.Length) } finally { $fs.Dispose() }";
                    RemoteExecResult part = execOn(client, script);
                    if (!part.success()) {
                        throw new IllegalStateException("分块上传失败: " + part.failureMessage());
                    }
                }
            }
            long remoteSize = fileSizeOn(client, remotePath);
            if (remoteSize >= 0 && remoteSize != localSize) {
                throw new IllegalStateException("上传后字节数不一致: 本地 " + localSize + " 字节, 远端 " + remoteSize + " 字节");
            }
        }
        return RemoteExecResult.ok("上传完成: " + remotePath + " (" + localSize + " 字节)");
    }

    @Override
    public RemoteExecResult mkdirs(MsServerHost host, SshCredential credential, String path) throws Exception {
        try (WinRmExecClient client = open(host, credential, 0)) {
            return mkdirsOn(client, path);
        }
    }

    @Override
    public RemoteExecResult clearDir(MsServerHost host, SshCredential credential, String dir) throws Exception {
        // 先删内容再确保目录存在；-Force 保证隐藏/只读项也被清理
        String script = "$d='" + psEscape(dir) + "';"
                + "if (Test-Path -LiteralPath $d) {"
                + "  Get-ChildItem -LiteralPath $d -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue"
                + "};"
                + "New-Item -ItemType Directory -Path $d -Force | Out-Null";
        return exec(host, credential, script, 0);
    }

    @Override
    public RemoteExecResult extract(MsServerHost host, SshCredential credential, String archivePath, String fileName, String dir) throws Exception {
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        String script;
        if (name.endsWith(".zip")) {
            script = "New-Item -ItemType Directory -Path '" + psEscape(dir) + "' -Force | Out-Null;"
                    + "Expand-Archive -LiteralPath '" + psEscape(archivePath) + "' -DestinationPath '" + psEscape(dir) + "' -Force";
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".tar")) {
            // Windows 10 1803+ 自带 bsdtar，可直接解 tar/tgz
            script = "New-Item -ItemType Directory -Path '" + psEscape(dir) + "' -Force | Out-Null;"
                    + "tar -xf '" + psEscape(archivePath) + "' -C '" + psEscape(dir) + "'";
        } else {
            return RemoteExecResult.ok(fileName + ": 非压缩包，跳过解压");
        }
        return exec(host, credential, script, 0);
    }

    @Override
    public RemoteExecResult remove(MsServerHost host, SshCredential credential, String path) throws Exception {
        String script = "Remove-Item -LiteralPath '" + psEscape(path) + "' -Recurse -Force -ErrorAction SilentlyContinue";
        return exec(host, credential, script, 0);
    }

    @Override
    public long fileSize(MsServerHost host, SshCredential credential, String file) {
        try (WinRmExecClient client = open(host, credential, 0)) {
            return fileSizeOn(client, file);
        } catch (Exception e) {
            log.debug("[remote] 文件大小探测失败 {}: {}", file, e.getMessage());
            return -1L;
        }
    }

    @Override
    public RemoteLogChunk tail(MsServerHost host, SshCredential credential, String file, Long cursor, int lines) throws Exception {
        long startCursor = cursor == null ? 0L : Math.max(0L, cursor);
        try (WinRmExecClient client = open(host, credential, 0)) {
            long size = fileSizeOn(client, file);
            // 日志轮转/截断：文件比上次记录的光标还小 → 当作新文件从头拉取
            if (size >= 0 && startCursor > size) {
                startCursor = 0L;
            }
            String script;
            if (startCursor > 0) {
                // 增量：按字节偏移定位后续读，末尾不做换行补全，避免污染日志
                script = "$ErrorActionPreference='Stop';"
                        + "$p='" + psEscape(file) + "';"
                        + "if (Test-Path -LiteralPath $p) {"
                        + "  $fs=[IO.File]::Open($p,'Open','Read','ReadWrite');"
                        + "  try {"
                        + "    $fs.Position=" + startCursor + ";"
                        + "    $sr=New-Object IO.StreamReader($fs,[Text.Encoding]::UTF8);"
                        + "    try { [Console]::Write($sr.ReadToEnd()) } finally { $sr.Dispose() }"
                        + "  } finally { $fs.Dispose() }"
                        + "}";
            } else {
                // 首帧：取末尾 N 行
                script = "$ErrorActionPreference='Stop';"
                        + "$p='" + psEscape(file) + "';"
                        + "if (Test-Path -LiteralPath $p) {"
                        + "  $rows=Get-Content -LiteralPath $p -Tail " + lines + " -Encoding UTF8;"
                        + "  [Console]::Write(($rows -join \"`n\"))"
                        + "}";
            }
            RemoteExecResult result = execOn(client, script);
            if (!result.success()) {
                throw new IllegalStateException("读取日志失败: " + result.failureMessage());
            }
            String content = result.stdout() == null ? "" : result.stdout();
            long nextCursor = size >= 0 ? size : startCursor + content.getBytes(StandardCharsets.UTF_8).length;
            return RemoteLogChunk.of(content, nextCursor);
        }
    }

    @Override
    public RemoteExecResult healthCheck(MsServerHost host, SshCredential credential, HostHealthType type, String target, int timeoutSeconds) throws Exception {
        int timeout = Math.max(timeoutSeconds, MIN_HEALTH_TIMEOUT_SECONDS);
        String probe = switch (type == null ? HostHealthType.PORT : type) {
            case PORT -> "if (Get-NetTCPConnection -LocalPort " + target
                    + " -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }";
            case HTTP -> "try { Invoke-WebRequest -Uri 'http://" + psEscape(target)
                    + "' -UseBasicParsing -TimeoutSec " + Math.min(timeout, HTTP_PROBE_MAX_SECONDS)
                    + " | Out-Null; exit 0 } catch { exit 1 }";
            case PROCESS -> "if (Get-Process -Name '" + psEscape(target)
                    + "' -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }";
        };
        return exec(host, credential, probe, timeout);
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
            // 端口号非数字时直接判为无数据：Get-NetTCPConnection 的参数绑定错误会以非 0 退出码返回，
            // 若在此抛出会污染整轮采集（调用方按「连接失败」短路后续命令）
            return Double.NaN;
        }
        // 全部以 PowerShell 脚本下发（exec 内部走 -EncodedCommand），因此只需处理 PowerShell 单引号转义
        String script = switch (type) {
            // Win32_Process 的 Name 只含可执行文件名，故按关键字 -match 匹配；累加工作集后换算 MB
            case PROCESS_MEM_MB -> "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '"
                    + psEscape(key) + "' }; "
                    + "if ($p) { [int](($p | Measure-Object -Property WorkingSetSize -Sum).Sum / 1MB) } else { -1 }";
            // Get-Process 的 CPU 是累计占用秒数：除以进程已运行秒数得到平均占用率，与 POSIX 侧 ps pcpu 口径一致
            case PROCESS_CPU -> "$p = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '"
                    + psEscape(key) + "' }; "
                    + "if ($p) { $cpu = ($p | Measure-Object -Property CPU -Sum).Sum; "
                    + "$st = ($p | Sort-Object StartTime | Select-Object -First 1).StartTime; "
                    + "$sec = ((Get-Date) - $st).TotalSeconds; "
                    + "if ($sec -gt 0) { [math]::Round($cpu / $sec * 100, 1) } else { -1 } } else { -1 }";
            case PORT_ALIVE -> "if (Get-NetTCPConnection -LocalPort " + key
                    + " -State Listen -ErrorAction SilentlyContinue) { 1 } else { 0 }";
            case HTTP_CODE -> "try { (Invoke-WebRequest -Uri 'http://" + psEscape(key)
                    + "' -UseBasicParsing -TimeoutSec " + Math.min(timeout, HTTP_PROBE_MAX_SECONDS)
                    + ").StatusCode } catch { 0 }";
        };
        RemoteExecResult result = exec(host, credential, script, timeout);
        if (!result.success()) {
            throw new IllegalStateException("指标采集失败(" + type.desc() + "): " + result.failureMessage());
        }
        return parseMetric(result.stdout());
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
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\\' && sb.charAt(sb.length() - 1) != '/') {
                sb.append(SEPARATOR);
            }
            sb.append(part);
        }
        return sb.toString();
    }

    @Override
    public String quotePath(String path) {
        return "'" + psEscape(path) + "'";
    }

    /**
     * 建立 WinRM 连接
     *
     * @param host           主机
     * @param credential     凭据（WinRM 仅支持用户名/密码）
     * @param timeoutSeconds 会话超时秒数，&lt;=0 取默认
     * @return 已连接客户端（调用方负责关闭）
     */
    private WinRmExecClient open(MsServerHost host, SshCredential credential, int timeoutSeconds) {
        if (credential == null || credential.username() == null || credential.username().isBlank()) {
            throw new IllegalStateException("Windows 主机需要用户名/密码凭据（WinRM 不支持密钥认证）");
        }
        if (credential.hasKey() && (credential.password() == null || credential.password().isBlank())) {
            throw new IllegalStateException("WinRM 不支持密钥认证，请为该主机配置密码凭据");
        }
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0
                ? DEFAULT_WINRM_PORT : host.getServerHostPort();
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        WinRmExecClient client = WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(port)
                .username(credential.username())
                .password(credential.password() == null ? "" : credential.password())
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .sessionTimeout(timeout * 1000)
                .build();
        client.connect();
        return client;
    }

    /**
     * 在既有连接上执行 PowerShell 脚本
     *
     * @param client  已连接客户端
     * @param script  PowerShell 脚本
     * @return 执行结果
     */
    private RemoteExecResult execOn(WinRmExecClient client, String script) {
        var result = client.exec().command(encode(script)).execute();
        return new RemoteExecResult(result.exitCode(), result.stdout(), result.stderr());
    }

    /**
     * 在既有连接上确保目录存在
     *
     * @param client 已连接客户端
     * @param path   远端目录
     * @return 执行结果
     */
    private RemoteExecResult mkdirsOn(WinRmExecClient client, String path) {
        return execOn(client, "New-Item -ItemType Directory -Path '" + psEscape(path) + "' -Force | Out-Null");
    }

    /**
     * 在既有连接上查询文件字节大小
     *
     * @param client 已连接客户端
     * @param file   远端文件
     * @return 字节数；不存在或探测失败为 -1
     */
    private long fileSizeOn(WinRmExecClient client, String file) {
        try {
            String script = "$p='" + psEscape(file) + "';"
                    + "if (Test-Path -LiteralPath $p) { [Console]::Write((Get-Item -LiteralPath $p).Length) } else { [Console]::Write('-1') }";
            RemoteExecResult result = execOn(client, script);
            if (!result.success() || result.stdout() == null || result.stdout().isBlank()) {
                return -1L;
            }
            return (long) Double.parseDouble(result.stdout().trim());
        } catch (Exception e) {
            log.debug("[remote] 文件大小探测失败 {}: {}", file, e.getMessage());
            return -1L;
        }
    }

    /**
     * 将 PowerShell 脚本编码为 {@code -EncodedCommand} 参数
     * <p>
     * 脚本按 UTF-16LE 编码后 Base64：完全绕开引号/换行/特殊字符的转义问题，
     * 多行脚本也能安全下发。
     * </p>
     *
     * @param script PowerShell 脚本
     * @return 完整命令行
     */
    private String encode(String script) {
        String text = script == null ? "" : script;
        String base64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_16LE));
        return "powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand " + base64;
    }

    /**
     * PowerShell 单引号字面量转义（内部单引号翻倍）
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

    /**
     * 取远端路径的父目录
     *
     * @param path 远端路径
     * @return 父目录；无分隔符时返回 null
     */
    private String parentOf(String path) {
        if (path == null) {
            return null;
        }
        int idx = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return idx > 0 ? path.substring(0, idx) : null;
    }
}
