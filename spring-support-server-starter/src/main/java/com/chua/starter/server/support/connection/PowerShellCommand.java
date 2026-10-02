package com.chua.starter.server.support.connection;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Windows 远端 PowerShell 下发工具。
 *
 * <p>所有 SSH / WinRM 通道下发 PowerShell 的出口都收敛到这里，避免各通道分别实现编码前缀与
 * {@code -EncodedCommand} 包装后再次走偏。</p>
 *
 * <p>实测事实（192.168.200.18，WS2012R2 zh-CN，PowerShell 4.0）：脚本内
 * {@code [Console]::OutputEncoding=UTF8} 在 sshd 的管道下不生效，中文仍按系统 OEM 代码页（936）
 * 写出并被按 UTF-8 解码成替换符；只有把控制台代码页切到 65001 才真正生效。故统一前缀
 * {@code cmd /c "chcp 65001 >nul & ..."}，SSH 与 WinRM 两条通道同一条链路。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class PowerShellCommand {

    /**
     * 优先使用的外壳：PowerShell 7。
     *
     * <p>原生 UTF-8、跨平台，且不受针对 Windows PowerShell 5.1 canonical 路径的拦截
     * （实测 360 只拦 {@code C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe}，
     * 同机 {@code pwsh} 正常）。</p>
     */
    public static final String MODERN_SHELL = "pwsh";

    /**
     * 回退外壳：Windows PowerShell 5.1（所有 Windows 都自带）
     */
    public static final String LEGACY_SHELL = "powershell";

    /**
     * 代码页前缀：chcp 的输出本身不需要，重定向掉以免污染首行
     */
    private static final String UTF8_PREFIX = "cmd /c \"chcp 65001 >nul & ";

    /**
     * 进度抑制前言：输出被重定向时，PowerShell 会把进度记录序列化成 CLIXML 写进 stderr，
     * 失败时这段 XML 就成了用户看到的错误原因。关闭进度流后 stderr 只剩真实错误。
     */
    private static final String QUIET_PROLOGUE = "$ProgressPreference='SilentlyContinue';\r\n";

    private PowerShellCommand() {
    }

    /**
     * 包装成切到 UTF-8 代码页后由 cmd 发起的 EncodedCommand 行（脚本按 UTF-16LE Base64）。
     *
     * <p>必须走 EncodedCommand：内联 {@code -Command "..."} 会被 cmd.exe 在第一个换行处截断，
     * 多行脚本只剩首行，且 PowerShell 的解析错误仍带着 0 退出码回来。
     * {@code -NonInteractive} 防止无人值守的远端卡在控制台提示上。</p>
     *
     * @param ps PowerShell 脚本
     * @return 完整命令行，脚本为空时也只是合法的纯前言命令
     */
    public static String encoded(String ps) {
        return encoded(LEGACY_SHELL, ps);
    }

    /**
     * 用指定外壳包装 EncodedCommand 行。
     *
     * <p>外壳可执行名由调用方给出，用于「优先 PowerShell 7、不可用再回退 Windows PowerShell」。
     * 见 {@link #looksLikeShellUnavailable(String)}。</p>
     *
     * @param shell 外壳可执行名（{@code pwsh} 或 {@code powershell}）
     * @param ps    PowerShell 脚本
     * @return 完整命令行
     */
    public static String encoded(String shell, String ps) {
        String body = QUIET_PROLOGUE + (ps == null ? "" : ps);
        String b64 = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_16LE));
        return UTF8_PREFIX + shell + " -NoProfile -NonInteractive -ExecutionPolicy Bypass -OutputFormat Text"
                + " -EncodedCommand " + b64 + "\"";
    }

    /**
     * 判断一次失败是否「这个外壳根本起不来」，用于决定是否换外壳重试。
     *
     * <p>覆盖两类：主机上不存在该外壳（{@code 'pwsh' 不是内部或外部命令} /
     * {@code 'pwsh' is not recognized}），以及外壳被安全产品按路径拦截
     * （{@code Access is denied.} / {@code 拒绝访问}，实测 360 只拦 Windows PowerShell 5.1
     * 的 canonical 路径，PowerShell 7 不受影响）。</p>
     *
     * @param stderr 远端 stderr
     * @return 像「外壳不可用」时返回 true
     */
    public static boolean looksLikeShellUnavailable(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return false;
        }
        String s = stderr.toLowerCase(Locale.ROOT);
        return s.contains("access is denied") || stderr.contains("拒绝访问")
                || s.contains("is not recognized") || stderr.contains("不是内部或外部命令")
                || s.contains("cannot find the file") || stderr.contains("系统找不到指定的文件");
    }
}
