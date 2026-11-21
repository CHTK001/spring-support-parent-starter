package com.chua.starter.monitor.support.support;

/**
 * 远端文件 stat 探针命令构造器。
 *
 * <p>上传覆盖判定（md5 + 更新时间）需要远端文件的存在性、大小、mtime 与 MD5。各通道以单条
 * 命令取证，统一输出 {@code STAT|size|mtimeMillis|md5} 或 {@code STAT|MISSING} 单行，由
 * {@link com.chua.starter.monitor.support.model.RemoteFileStatVO#parse} 统一解析。脚本文本只在这里
 * 拼装，通道实现只负责执行与各自传输层的引号转义。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class RemoteFileStatProbe {

    private RemoteFileStatProbe() {
    }

    /**
     * POSIX（Linux/macOS）sh 取证脚本。
     *
     * <p>GNU stat（{@code -c}）优先，BSD/macOS stat（{@code -f}）兜底；md5sum（Linux）优先，
     * {@code md5 -q}（macOS）兜底。注意 stat 的 mtime 单位是秒，这里统一乘 1000 折算成
     * epoch 毫秒后再回显；取值失败输出空段，由解析方按「信息不可得」处理，
     * 不让单次取证失败拖垮整个上传。</p>
     *
     * @param shQuotedPath 已按 sh 规则转义的单引号路径字面量
     * @return sh 脚本
     */
    public static String posix(String shQuotedPath) {
        String p = shQuotedPath;
        return "if [ -f " + p + " ]; then "
                + "s=$(stat -c '%s' " + p + " 2>/dev/null || stat -f '%z' " + p + "); "
                + "m=$(stat -c '%Y' " + p + " 2>/dev/null || stat -f '%m' " + p + "); "
                + "[ -n \"$m\" ] && m=$((m*1000)); "
                + "h=$(md5sum " + p + " 2>/dev/null | awk '{print $1}'); "
                + "[ -z \"$h\" ] && h=$(md5 -q " + p + " 2>/dev/null); "
                + "echo \"STAT|$s|$m|$h\"; else echo 'STAT|MISSING'; fi";
    }

    /**
     * Windows PowerShell 取证脚本。
     *
     * <p>脚本内只使用单引号字符串，便于经 cmd 双引号参数承载；MD5 取不到（文件被占用等）
     * 输出空段，不影响 size 与 mtime 的取证。mtime 用
     * {@code ([DateTimeOffset]$i.LastWriteTime).ToUnixTimeMilliseconds()} 取 epoch 毫秒——
     * 括号必不可少：不带括号时 cast 会作用在方法返回值上而不是 LastWriteTime 上。</p>
     *
     * @param psQuotedPath 已按 PowerShell 规则转义的单引号路径字面量
     * @return PowerShell 脚本
     */
    public static String windows(String psQuotedPath) {
        return "$p=" + psQuotedPath + "; "
                + "if (Test-Path -LiteralPath $p -PathType Leaf) { "
                + "$i=Get-Item -LiteralPath $p; $h=''; "
                + "try { $h=(Get-FileHash -LiteralPath $p -Algorithm MD5).Hash.ToLower() } catch {}; "
                + "Write-Output ('STAT|' + $i.Length + '|' + "
                + "([DateTimeOffset]$i.LastWriteTime).ToUnixTimeMilliseconds() + '|' + $h) "
                + "} else { Write-Output 'STAT|MISSING' }";
    }

    /**
     * sh 单引号字面量：内部 {@code '} 转义为 {@code '\\''}。
     *
     * @param value 原始路径
     * @return 单引号安全串
     */
    public static String shQuote(String value) {
        String v = value == null ? "" : value;
        return "'" + v.replace("'", "'\\''") + "'";
    }

    /**
     * PowerShell 单引号字面量：内部 {@code '} 翻倍。
     *
     * @param value 原始路径
     * @return 单引号安全串
     */
    public static String psQuote(String value) {
        String v = value == null ? "" : value;
        return "'" + v.replace("'", "''") + "'";
    }
}
