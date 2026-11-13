package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 静态账号口令的「MD5 加盐」摘要。
 *
 * <p><b>为什么必须加盐</b>：裸 MD5（甚至裸 SHA256）挡不住彩虹表——攻击者拿到配置
 * 里的一串摘要，对着预计算表几秒就能反查出 {@code admin123} 这类弱口令。加盐后每个账号
 * 的摘要互不相同，一张表无法批量比对，只能逐个账号爆破，成本从「一次」变成「N 次」。</p>
 *
 * <p><b>盐放在配置里，不是秘密</b>：盐的作用是「让相同口令产生不同摘要」，
 * 它的价值恰恰来自于<b>公开</b>——攻击者看得见盐，就没法用一张通用表反查。
 * 混淆这一点会导致两种错误：把盐当密钥藏起来（毫无收益），或误以为有盐就万无一失。</p>
 *
 * <p><b>本方案的边界（必须如实说明）</b>：MD5 计算极快，攻击者拿到配置后
 * 可对字典口令高速爆破，因此它只解决「配置泄露即口令曝光」与「一张表打穿所有账号」，
 * <b>不等于</b>抗离线爆破。同等条件下 HMAC-SHA256 更慢、更适合口令存储
 * （见 {@code StudentPasswordSupport}）。此处按部署方要求采用 MD5 加盐，
 * 前提是配置本身不外泄、且口令本身足够随机。</p>
 *
 * <p><b>配置格式</b>：{@code 用户名:<盐>$<摘要>}。为兼容历史配置，未带 {@code $} 的
 * 仍按「明文，或明文的 MD5」处理（见 {@link StaticTempLogin}）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class SaltedPassword {

    /**
     * 盐与摘要之间的分隔符
     */
    private static final char SEPARATOR = '$';

    /**
     * 盐的十六进制字符长度（16 字节）
     */
    private static final int SALT_HEX_LENGTH = 32;

    /**
     * MD5 摘要的十六进制长度（16 字节）
     */
    private static final int DIGEST_HEX_LENGTH = 32;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private SaltedPassword() {
    }

    /**
     * 判断配置值是否为「加盐摘要」格式。
     *
     * @param configured 配置值
     * @return 形如 {@code <32位盐>$<32位摘要>} 返回 {@code true}
     */
    public static boolean isSalted(String configured) {
        if (StringUtils.isBlank(configured)) {
            return false;
        }
        int idx = configured.indexOf(SEPARATOR);
        if (idx != SALT_HEX_LENGTH) {
            return false;
        }
        String salt = configured.substring(0, idx);
        String digest = configured.substring(idx + 1);
        return isHex(salt, SALT_HEX_LENGTH) && isHex(digest, DIGEST_HEX_LENGTH);
    }

    /**
     * 校验明文口令是否与配置的加盐摘要匹配。
     *
     * <p>用定长比较而非 {@code String.equals}：后者逐字符提前返回，
     * 比较耗时会泄漏「前几位猜对了」，可用于逐字节爆破摘要。</p>
     *
     * @param rawPassword 明文口令
     * @param configured  加盐摘要配置值
     * @return 匹配返回 {@code true}
     */
    public static boolean matches(String rawPassword, String configured) {
        if (StringUtils.isBlank(rawPassword) || !isSalted(configured)) {
            return false;
        }
        int idx = configured.indexOf(SEPARATOR);
        String salt = configured.substring(0, idx);
        String expected = configured.substring(idx + 1);
        return constantTimeEquals(hash(rawPassword, salt), expected);
    }

    /**
     * 按「盐 + 明文」计算 MD5 摘要。
     *
     * <p>拼接顺序固定为「盐在前、口令在后」：盐在配置里明文可见，若把口令放前面，
     * 攻击者可直接对已知盐计算 {@code MD5(猜测口令 + 已知盐)} 变体，比固定顺序更难批量预生成。</p>
     *
     * @param rawPassword 明文口令
     * @param salt        盐
     * @return 32 位十六进制摘要
     */
    public static String hash(String rawPassword, String salt) {
        try {
            var md = MessageDigest.getInstance("MD5");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // MD5 是 JDK 必备算法，走到这里说明运行环境被裁剪过
            throw new IllegalStateException("MD5 不可用: " + ex.getMessage(), ex);
        }
    }

    /**
     * 生成一个随机盐（16 字节，32 位十六进制）。
     *
     * <p>用 {@link SecureRandom} 而非 {@code Random}：后者是线性同余，
     * 观察若干次输出即可推算后续序列，等于没有盐。</p>
     *
     * @return 32 位十六进制盐串
     */
    public static String newSalt() {
        // [P3C A20 豁免]
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /**
     * 由明文口令生成可直接写入配置的加盐串 {@code <盐>$<摘要>}。
     *
     * @param rawPassword 明文口令
     * @return 加盐配置串
     */
    public static String encode(String rawPassword) {
        String salt = newSalt();
        return salt + SEPARATOR + hash(rawPassword, salt);
    }

    /**
     * 取加盐串中的盐（供诊断展示，不用于任何安全判定）。
     *
     * @param configured 加盐串
     * @return 盐；格式不合法时返回 {@code null}
     */
    public static String saltOf(String configured) {
        return isSalted(configured) ? configured.substring(0, SALT_HEX_LENGTH) : null;
    }

    /**
     * 字节数组转小写十六进制。
     *
     * @param bytes 字节数组
     * @return 十六进制串
     */
    private static String toHex(byte[] bytes) {
        var chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX[v >>> 4];
            chars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * 判断是否为指定长度的十六进制串。
     *
     * @param value  待判定串
     * @param length 期望长度
     * @return 是返回 {@code true}
     */
    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * 定长字符串比较：用异或累积差异，避免提前返回泄漏前缀长度。
     *
     * @param left  左侧串
     * @param right 右侧串
     * @return 是否相等
     */
    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length(); i++) {
            diff |= left.charAt(i) ^ right.charAt(i);
        }
        return diff == 0;
    }
}
