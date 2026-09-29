package com.chua.starter.monitor.support.nginx.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.nginx.spi.NginxCommandProvider;
import com.chua.starter.monitor.support.spi.Platforms;

/**
 * Windows（{@code cmd}）平台的 nginx 命令文本。
 *
 * <p>对照 {@link PosixNginxCommandProvider} 逐条给出等价实现。几处<strong>不能照搬</strong>的坑，
 * 都是在本机 Windows + nginx 1.28.0（{@code C:\nginx}）上实测确认的：</p>
 * <ul>
 *   <li><strong>单引号不是引号</strong>：{@code cmd} 只认双引号，路径用单引号包起来会被
 *       当成文件名的一部分。</li>
 *   <li><strong>跨盘符要 {@code cd /d}</strong>：只 {@code cd} 不切盘符，工作目录仍在原盘，
 *       相对路径全错。</li>
 *   <li><strong>没有 {@code tail} / {@code wc} / {@code find} / {@code ls -t}</strong>：
 *       分别用 {@code powershell} 的 {@code -Tail}、{@code .Length}、{@code -Recurse}、
 *       按 {@code LastWriteTime} 排序替代。</li>
 *   <li><strong>没有 systemd</strong>：服务控制退化为 {@code sc}；nginx 默认不是 Windows
 *       服务，因此「托管单元」能力恒为空，由调用方跳过而不是报错。</li>
 * </ul>
 *
 * <p>{@code order = 190} 低于 POSIX 的 200：Linux 上先试 {@code uname -s}，
 * 一步就能判定，不必浪费第二个来回。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "nginx-windows", order = 190)
public class WindowsNginxCommandProvider implements NginxCommandProvider {

    /**
     * 单次增量读取的字节上限（256KB），与 POSIX 实现保持一致。
     */
    private static final int MAX_DELTA_BYTES = 262144;

    /**
     * Windows 自证命令。
     *
     * <p>用 {@code ver} 而不是 {@code uname}：{@code ver} 是 {@code cmd} 内置命令，
     * 不需要额外程序，输出形如 {@code Microsoft Windows [版本 10.0.26100.6584]}——
     * 即使本地化把「版本」翻译了，{@code Windows} 这个产品名不会变。</p>
     */
    private static final String PROBE = "ver";

    @Override
    public boolean supports(String platform) {
        return Platforms.WINDOWS.equals(platform);
    }

    @Override
    public String name() {
        return "nginx-windows";
    }

    @Override
    public String quote(String value) {
        String v = value == null ? "" : value;
        // cmd 无法在双引号内转义双引号；路径里出现引号属于非法输入，直接剔除，
        // 否则会被后续命令当成语法，造成命令注入。
        return "\"" + v.replace("\"", "") + "\"";
    }

    @Override
    public String probePlatform() {
        return PROBE;
    }

    @Override
    public String parsePlatform(String probeOutput) {
        String text = probeOutput == null ? "" : probeOutput;
        return text.contains("Windows") || text.contains("windows") ? Platforms.WINDOWS : "";
    }

    @Override
    public boolean looksLikeError(String output) {
        String text = output == null ? "" : output;
        // 中文 Windows 与英文 Windows 的失败提示都要认
        return text.contains("不是内部或外部命令")
                || text.contains("系统找不到指定的路径")
                || text.contains("系统找不到指定的文件")
                || text.contains("not recognized")
                || text.contains("The system cannot find")
                || text.contains("拒绝访问")
                || text.contains("Access is denied");
    }

    @Override
    public String serviceControl(String unit, String action) {
        // sc 的动作词与 systemctl 不同：stop/start 同名，restart 要拆成两次调用由调用方负责；
        // 这里把 sc 的原生动作词映射出来
        String scAction = "restart".equals(action) ? "stop" : action;
        return "sc " + scAction + " " + quote(unit);
    }

    @Override
    public String serviceUnitOfProcess() {
        // Windows 上 nginx 通常不是服务，没有 systemd 那样的 ExecStart 反查；
        // 返回空串 = 该平台不支持此能力，调用方必须跳过而不是当失败
        return "";
    }

    @Override
    public String serviceMainPid(String unit) {
        return "";
    }

    @Override
    public String processAlive(String confTail) {
        // 刻意不做「按 conf 区分同名实例」：nginx 在 Windows 上通常只有一个实例，
        // 而要拿到命令行就得在 cmd → powershell 两层引号里再嵌一层，转义极易出错。
        // 宁可先给一个不会说谎的「有没有 nginx 在跑」，也不给一个可能认错实例的复杂脚本。
        return "powershell -NoProfile -Command \"if (Get-Process nginx -ErrorAction SilentlyContinue) { 'ON' } else { 'OFF' }\"";
    }

    @Override
    public String processPid(String confTail) {
        return "powershell -NoProfile -Command \"$p = Get-Process nginx -ErrorAction SilentlyContinue | Select-Object -First 1; if ($p) { $p.Id }\"";
    }

    @Override
    public String readFile(String path) {
        // type 是 cmd 内建命令，不认正斜杠；实测 type "C:/nginx/conf/nginx.conf" 会报
        // 「系统找不到指定的文件」，而同一路径在 PowerShell 里正常。故这里统一转反斜杠。
        return "type " + quote(winPath(path));
    }

    @Override
    public String fileExists(String path) {
        // if exist 是 cmd 内置判定，退出码 0/1 与 test -f 语义一致
        return "if exist " + quote(winPath(path)) + " (exit 0) else (exit 1)";
    }

    @Override
    public String makeDirs(String dir) {
        return "if not exist " + quote(winPath(dir)) + " mkdir " + quote(winPath(dir));
    }

    @Override
    public String copyFile(String source, String destination) {
        return "copy /Y " + quote(winPath(source)) + " " + quote(winPath(destination));
    }

    @Override
    public String listByModifiedDesc(String dir, String suffix) {
        String pattern = "*" + suffix;
        return "powershell -NoProfile -Command \""
                + "Get-ChildItem -LiteralPath " + psQuote(dir) + " -Filter " + psQuote(pattern)
                + " -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | ForEach-Object { $_.FullName }\"";
    }

    @Override
    public String tailLines(String path, int lines) {
        // Get-Content -Tail 等价于 tail -n；不需要 -Wait
        return "powershell -NoProfile -Command \""
                + "Get-Content -LiteralPath " + psQuote(path) + " -Tail " + lines + " -ErrorAction SilentlyContinue\"";
    }

    @Override
    public String fileSize(String path) {
        return "powershell -NoProfile -Command \""
                + "(Get-Item -LiteralPath " + psQuote(path) + " -ErrorAction SilentlyContinue).Length\"";
    }

    @Override
    public String bytesFrom(String path, long start) {
        // 从第 start 字节（0 基）起读，最多 MAX_DELTA_BYTES，
        // 按 UTF-8 解出，避免中文日志乱码
        return "powershell -NoProfile -Command \""
                + "$fs=[System.IO.File]::Open(" + psQuote(winPath(path)) + ",'Open','Read','ReadWrite');"
                + "try{$fs.Seek(" + start + ",'Begin')|Out-Null;"
                + "$buf=New-Object byte[] " + MAX_DELTA_BYTES + ";"
                + "$n=$fs.Read($buf,0," + MAX_DELTA_BYTES + ");"
                + "[System.Text.Encoding]::UTF8.GetString($buf,0,$n)}finally{$fs.Close()}\"";
    }

    @Override
    public String findFiles(String root, String pattern) {
        // 同 listFilesWithMeta：路径统一正斜杠，避免调用方按 '/' 约定处理时出错
        return "powershell -NoProfile -Command \""
                + "Get-ChildItem -LiteralPath " + psQuote(root) + " -Filter " + psQuote(pattern)
                + " -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName -replace '\\\\','/' }\"";
    }

    @Override
    public String listFilesWithMeta(String root, String namePattern, boolean recursive) {
        // 用 [char]9 表示制表符，避免在 cmd → powershell 两层引号里再嵌 \t 的转义。
        // 路径统一转正斜杠：调用方（服务）内部按 '/' 约定拼路径与派生备份名
        // （如 backup() 的 safePath.replace('/', '_')），返回反斜杠会派生出台非法文件名
        // （含 C:\ 与 ':'），实测导致 copy 报「文件名语法不正确」。
        String recurse = recursive ? " -Recurse" : "";
        return "powershell -NoProfile -Command \""
                + "Get-ChildItem -LiteralPath " + psQuote(root) + " -Filter " + psQuote(namePattern) + recurse
                + " -File -ErrorAction SilentlyContinue | ForEach-Object { "
                + "($_.FullName -replace '\\\\','/') + [char]9 + $_.Length + [char]9 + $_.LastWriteTime.ToString('yyyy-MM-dd HH:mm') }\"";
    }

    @Override
    public String writeFile(String path, String base64Content) {
        // Windows 没有 base64 命令。用 .NET 直接解码落盘，绕开所有引号/转义层。
        // 注意：命令长度受 cmd 约 8K 上限约束，与 POSIX 侧同样是既有局限；
        // 站点配置通常几 KB，够用。
        return "powershell -NoProfile -Command \""
                + "$b=[Convert]::FromBase64String(" + psQuote(base64Content) + ");"
                + "[System.IO.File]::WriteAllBytes(" + psQuote(winPath(path)) + ",$b)\"";
    }

    @Override
    public String dirExists(String path) {
        // 不能用 `if exist "path\*"`：那只在目录非空时为真，空目录会误判为不存在。
        // Test-Path -PathType Container 对空目录同样正确。
        return "powershell -NoProfile -Command \""
                + "if (Test-Path -LiteralPath " + psQuote(winPath(path)) + " -PathType Container)"
                + " { exit 0 } else { exit 1 }\"";
    }

    @Override
    public String parentDir(String path) {
        // 不能用 Split-Path -LiteralPath X -Parent：实测 PowerShell 报
        // AmbiguousParameterSet（「Parameter set cannot be resolved」），exit=1、无输出，
        // 于是 prefix 解析成空串、conf 路径退化为相对、nginx -t 找不到配置。
        // [System.IO.Path]::GetDirectoryName 无参数集歧义，且不依赖当前工作目录。
        return "powershell -NoProfile -Command \""
                + "[System.IO.Path]::GetDirectoryName(" + psQuote(winPath(path)) + ")\"";
    }

    @Override
    public String homeDir() {
        return "echo %USERPROFILE%";
    }

    @Override
    public String directiveMatches(String root, String namePattern, String directive, int maxLines) {
        // .NET 正则不认 [[:space:]]，用 \s；语义与原 POSIX 写法一致
        String regex = directive + "\\s+[^;]+";
        String limit = maxLines > 0 ? " | Select-Object -First " + maxLines : "";
        return "powershell -NoProfile -Command \""
                + "Get-ChildItem -LiteralPath " + psQuote(root) + " -Filter " + psQuote(namePattern) + " -Recurse -File"
                + " -ErrorAction SilentlyContinue | ForEach-Object { "
                + "Select-String -LiteralPath $_.FullName -Pattern " + psQuote(regex) + " -AllMatches"
                + " -ErrorAction SilentlyContinue | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value } }"
                + limit + "\"";
    }

    @Override
    public String which(String command) {
        return "where " + quote(command);
    }

    @Override
    public String versionDump(String binary) {
        // nginx -V 把编译参数写到 stderr，2>&1 必须保留
        return binary(binary) + " -V 2>&1";
    }

    @Override
    public String listenRows() {
        return "netstat -ano -p tcp";
    }

    @Override
    public String nginxInvocation(String binary, String prefix, String conf, String action) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            // /d 切盘符；只 cd 的话工作目录还在原盘，相对路径全错
            sb.append("cd /d ").append(quote(prefix)).append(" && ");
        }
        sb.append(binary(binary));
        if (conf != null && !conf.isBlank()) {
            sb.append(" -c ").append(quote(conf));
        }
        String act = action == null ? "" : action.trim();
        if (!act.isEmpty()) {
            sb.append(' ').append(act);
        }
        return sb.toString();
    }

    /**
     * nginx 可执行文件：空则用 {@code nginx}（依赖 PATH）。
     *
     * <p>非静态：内部要用 {@link #quote(String)}，那是接口的实例方法。</p>
     *
     * @param binary 配置的可执行文件
     * @return 实际使用的命令名
     */
    private String binary(String binary) {
        return binary == null || binary.isBlank() ? "nginx" : quote(binary);
    }

    /**
     * 把路径统一成反斜杠形式，供 cmd 内建命令使用。
     *
     * <p>{@code type} / {@code mkdir} / {@code copy} 这些 cmd 内建命令不认正斜杠，
     * 而 PowerShell 两者都认。所以只在走 cmd 的命令上转，PowerShell 的保持原样
     * （少一次无谓改写，也避免盘符后出现 {@code \\} 之类的边角情况）。</p>
     *
     * @param path 原始路径
     * @return 反斜杠路径
     */
    private static String winPath(String path) {
        return path == null ? "" : path.replace('/', '\\');
    }

    /**
     * PowerShell 单引号字面量：内部的单引号要写成两个。
     *
     * @param value 原始字面量
     * @return 可安全嵌入 {@code -Command} 的字面量
     */
    private static String psQuote(String value) {
        return "'" + (value == null ? "" : value).replace("'", "''") + "'";
    }
}
