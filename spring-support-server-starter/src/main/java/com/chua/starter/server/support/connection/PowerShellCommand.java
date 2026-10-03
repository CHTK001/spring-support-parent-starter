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
     * 内联 {@code -EncodedCommand} 的 base64 安全上限。
     *
     * <p><b>这是实测踩出来的硬上限，不是保守估计。</b>脚本按 UTF-16LE 编码再 base64，
     * 膨胀到 4/3；外面还要套 {@code cmd /c "chcp 65001 >nul & pwsh ... -EncodedCommand "}
     * （约 150 字符固定外壳）。{@code cmd.exe} 的命令行上限 8191 字符，超出即整条命令被拒：
     * 实测 base64 6104 正常、8072 起必定失败，悬崖落在 base64 ≈ 8040。
     * 症状是 <b>0 秒退出、exit 1、且错误信息是十来个别名（U+FFFD）</b>——
     * 也就是说超限时既失败又完全看不出原因。</p>
     *
     * <p>取 7000 而不是贴着 8040，是为了给固定外壳留余量；这个余量不能省，
     * 因为超限的失败形态是「别名错误」，现场根本无法判断是长度问题。</p>
     *
     * <p>真实脚本尺寸：Windows 启动段 base64 6612~6720（仍在限内，所以「启动」一直好用），
     * 重启段 9116（超限），带三段式健康检查的升级/回滚命令 29076（远超）。
     * 「启动能用、重启和升级必挂」这种现象的根因就在这里，而不是脚本内容有问题。</p>
     */
    public static final int INLINE_BASE64_LIMIT = 7000;

    /**
     * 完整脚本正文（含进度抑制前言），即内联执行时实际交给外壳的内容。
     *
     * @param ps 业务脚本
     * @return 前言 + 业务脚本
     */
    public static String body(String ps) {
        return QUIET_PROLOGUE + (ps == null ? "" : ps);
    }

    /**
     * 脚本内联下发时的 base64 长度。
     *
     * @param ps 业务脚本
     * @return base64 字符数
     */
    public static int base64Length(String ps) {
        return Base64.getEncoder().encodeToString(body(ps).getBytes(StandardCharsets.UTF_16LE)).length();
    }

    /**
     * 判断脚本是否超出内联下发能力。
     *
     * @param ps 业务脚本
     * @return 超出内联上限时为 {@code true}，需要改走「落盘 + -File」
     */
    public static boolean exceedsInlineLimit(String ps) {
        return base64Length(ps) > INLINE_BASE64_LIMIT;
    }

    /**
     * 改用 {@code -File} 执行远端已落盘的脚本。
     *
     * <p>脚本本体不再挤进命令行，因此不受 {@code cmd.exe} 的 8191 字符上限约束；
     * 外层仍保留 {@code chcp 65001} 前缀，与内联路径保持同一套编码约定。</p>
     *
     * @param shell      外壳可执行名
     * @param remotePath 远端脚本绝对路径
     * @return 完整命令行
     */
    public static String fileBased(String shell, String remotePath) {
        return UTF8_PREFIX + shell + " -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \""
                + remotePath + "\"";
    }

    /**
     * 包装成切到 UTF-8 代码页后由 cmd 发起的 EncodedCommand 行（脚本按 UTF-16LE Base64）。
     *
     * <p>必须走 EncodedCommand：内联 {@code -Command "..."} 会被 cmd.exe 在第一个换行处截断，
     * 多行脚本只剩首行，且 PowerShell 的解析错误仍带着 0 退出码回来。
     * {@code -NonInteractive} 防止无人值守的远端卡在控制台提示上。</p>
     *
     * <p>长度超过 {@link #INLINE_BASE64_LIMIT} 时不要用本方法，请改走
     * {@link #fileBased(String, String)}。</p>
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
        String b64 = Base64.getEncoder().encodeToString(body(ps).getBytes(StandardCharsets.UTF_16LE));
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
