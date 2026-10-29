package com.chua.starter.server.support.provider.remote;


import com.chua.common.support.spi.annotations.Spi;
import com.chua.ssh.support.client.SftpClient;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.connection.PowerShellCommand;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostHealthType;
import com.chua.starter.server.support.spi.HostMetricType;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostShellDialect;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.spi.RemoteLogChunk;
import com.chua.starter.server.support.util.SshClients;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Windows 主机远程操作实现（OpenSSH + PowerShell）
 * <p>
 * 除 RDP 远控外，Windows 的命令执行、文件分发、指标采集、健康探测等全部走 OpenSSH：
 * PowerShell 脚本统一以 {@code -EncodedCommand}（UTF-16LE + Base64）下发，由 Windows
 * 自带 OpenSSH 的默认外壳（cmd / powershell）拉起 powershell 执行，规避多行脚本与引号
 * 的转义问题；文件传输走同端口的 SFTP，不再有 WinRM 分块上传的大小限制。
 * </p>
 *
 * <p>
 * 前提：目标机必须安装并启动 OpenSSH Server（sshd）。可由主机编辑页的「安装/启用 OpenSSH」
 * 动作经现有通道一次性引导安装，之后全部操作均走 OpenSSH。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "windows-ssh", order = 100)
public class WindowsHostRemoteProvider implements HostRemoteProvider {

    /**
     * 默认 OpenSSH 端口
     */
    private static final int DEFAULT_SSH_PORT = 22;

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
     * 远端临时目录
     */
    private static final String TEMP_DIR = "C:\\Windows\\Temp";

    /**
     * SSH 连接缓存管理器（复用已建立连接，避免每次执行重复握手）
     */
    @Autowired
    private ConnectionManager connectionManager;

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
        // SSH 通道下发的是 PowerShell 脚本（-EncodedCommand），AI 生成脚本与高危审计都须按 PS 语法
        return HostShellDialect.POWERSHELL;
    }

    @Override
    public RemoteExecResult exec(MsServerHost host, SshCredential credential, String command, int timeoutSeconds) throws Exception {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            return execOn(handle.client(), command);
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    @Override
    public RemoteExecResult upload(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception {
        long localSize = Files.size(localFile);
        String parent = parentOf(remotePath);
        if (parent != null) {
            // PowerShell New-Item -Force 会递归创建中间目录
            SshClients.Handle mkHandle = null;
            try {
                mkHandle = connectionManager.borrow(host, credential);
                RemoteExecResult mk = execOn(mkHandle.client(),
                        "New-Item -ItemType Directory -Path '" + psEscape(parent) + "' -Force | Out-Null");
                if (!mk.success()) {
                    throw new IllegalStateException("创建远端目录失败: " + mk.failureMessage());
                }
            } finally {
                if (mkHandle != null) {
                    connectionManager.release(host.getId());
                }
            }
        }
        // SFTP 流式上传，无大小限制（复用借用连接的会话，不新建连接）
        SshClients.Handle sftpHandle = null;
        try {
            sftpHandle = connectionManager.borrow(host, credential);
            try (SftpClient channel = sftpHandle.client().sftp()) {
                channel.upload().local(localFile.toString()).remote(remotePath).exec();
            }
        } finally {
            if (sftpHandle != null) {
                connectionManager.release(host.getId());
            }
        }
        return RemoteExecResult.ok("上传完成: " + remotePath + " (" + localSize + " 字节)");
    }

    @Override
    public RemoteExecResult mkdirs(MsServerHost host, SshCredential credential, String path) throws Exception {
        return exec(host, credential,
                "New-Item -ItemType Directory -Path '" + psEscape(path) + "' -Force | Out-Null", 0);
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
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
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
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            return fileSizeOn(handle.client(), file);
        } catch (Exception e) {
            log.debug("[remote] 文件大小探测失败 {}: {}", file, e.getMessage());
            return -1L;
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    @Override
    public RemoteLogChunk tail(MsServerHost host, SshCredential credential, String file, Long cursor, int lines) throws Exception {
        long startCursor = cursor == null ? 0L : Math.max(0L, cursor);
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            return tailBorrowed(handle.client(), file, startCursor, lines, cursor);
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
     * 在借用连接上读取远端日志（调用方负责归还连接）
     */
    private RemoteLogChunk tailBorrowed(SshClient client, String file, long startCursor, int lines, Long cursor) throws Exception {
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
            // 端口号非数字时直接判为无数据，避免把任意串拼进命令（注入风险）
            return Double.NaN;
        }
        // 全部以 PowerShell 脚本下发（exec 内部走 -EncodedCommand），只需处理 PowerShell 单引号转义
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
     * SSH 端口：优先主机登录端口，否则回退 22
     *
     * @param host 主机
     * @return 端口
     */
    private int portOf(MsServerHost host) {
        return host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : DEFAULT_SSH_PORT;
    }

    /**
     * 在既有 SSH 连接上执行 PowerShell 脚本（内部包 EncodedCommand）
     *
     * @param client 已连接 SSH 客户端
     * @param script PowerShell 脚本
     * @return 执行结果
     */
    private RemoteExecResult execOn(SshClient client, String script) {
        // 优先 PowerShell 7、失败回退 5.1：实测安全产品可能只拦 5.1 的 canonical 路径
        RemoteExecResult r = execOnce(client, PowerShellCommand.MODERN_SHELL, script);
        if (r.success() || !PowerShellCommand.looksLikeShellUnavailable(r.stderr())) {
            return r;
        }
        return execOnce(client, PowerShellCommand.LEGACY_SHELL, script);
    }

    /**
     * 用指定外壳执行一次并做失败诊断。
     *
     * @param client 已连接 SSH 客户端
     * @param shell  外壳可执行名
     * @param script PowerShell 脚本
     * @return 执行结果（失败时 stderr 追加排查提示）
     */
    private RemoteExecResult execOnce(SshClient client, String shell, String script) {
        try {
            SshClient.ExecResult result = client.exec().command(encode(shell, script)).execute();
            RemoteExecResult r = new RemoteExecResult(result.exitCode(), result.stdout(), result.stderr());
            if (!r.success()) {
                // PowerShell 起不来时远端以「正常返回 + 非零退出码 + stderr」的形式回传，
                // 并不抛异常，因此提示必须补在结果分支上，否则操作员只能看到裸的
                // "Access is denied." 而不知从何查起。
                String hint = diagnosePowerShellLaunch(r.stderr());
                if (!hint.isEmpty()) {
                    String err = r.stderr() == null ? "" : r.stderr();
                    return new RemoteExecResult(r.exitCode(), r.stdout(), err + hint);
                }
            }
            return r;
        } catch (Exception e) {
            throw new IllegalStateException("PowerShell 执行失败: " + e.getMessage()
                    + diagnosePowerShellLaunch(e.getMessage()), e);
        }
    }

    /**
     * 针对「PowerShell 起不来」追加可执行的排查提示。
     *
     * <p>远端只回 {@code Access is denied.} 时，最容易被误判成「账户权限不足」，
     * 于是去查账户/ACL/sshd 配置，全部正常后无从下手。实测存在一类完全不同的成因：
     * 第三方安全产品按**精确路径**拦截
     * {@code C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe} 的执行——
     * 同一条二进制拷到别的目录就能跑、只有原路径被拒。此时账户、ACL、sshd 配置都正常，
     * 平台也无自愈路径（{@code ensure-ssh} 自身就走这条通道）。</p>
     *
     * @param message 原始错误信息
     * @return 追加的提示文本；不匹配特定措辞时返回空串
     */
    private String diagnosePowerShellLaunch(String message) {
        if (message == null) {
            return "";
        }
        // 中英文本地化措辞都要认：Windows 中文版报「拒绝访问。」
        if (message.toLowerCase(Locale.ROOT).contains("access is denied") || message.contains("拒绝访问")) {
            return "（排查提示：该错误未必是权限问题。若主机装有第三方安全产品，"
                    + "它可能按精确路径拦截 powershell.exe 的执行——"
                    + "判据是「把 powershell.exe 复制到其它目录能运行、而原路径被拒」。"
                    + "详见 src/acceptance/WIN-EXEC-DENIED-CHECKLIST.md）";
        }
        return "";
    }

    /**
     * 在既有 SSH 连接上查询文件字节大小
     *
     * @param client 已连接 SSH 客户端
     * @param file   远端文件
     * @return 字节数；不存在或探测失败为 -1
     */
    private long fileSizeOn(SshClient client, String file) {
        String script = "$p='" + psEscape(file) + "';"
                + "if (Test-Path -LiteralPath $p) { [Console]::Write((Get-Item -LiteralPath $p).Length) } else { [Console]::Write('-1') }";
        RemoteExecResult result = execOn(client, script);
        if (!result.success() || result.stdout() == null || result.stdout().isBlank()) {
            return -1L;
        }
        try {
            return (long) Double.parseDouble(result.stdout().trim());
        } catch (NumberFormatException e) {
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
        return PowerShellCommand.encoded(script);
    }

    /**
     * 用指定外壳编码（配合「优先 pwsh、失败回退 powershell」）。
     *
     * @param shell  外壳可执行名
     * @param script PowerShell 脚本
     * @return 完整命令行
     */
    private String encode(String shell, String script) {
        return PowerShellCommand.encoded(shell, script);
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