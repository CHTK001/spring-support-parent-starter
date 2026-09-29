package com.chua.starter.eduai.support.util;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.exception.BusinessException;

import java.security.SecureRandom;

/**
 * 学生登录口令的生成与校验。
 *
 * <p><b>为什么不加盐的裸摘要也不加明文</b>：明文落库意味着任何一次库泄露或备份外泄
 * 都直接等于所有学生口令曝光；裸 SHA256 则会被一张预计算表批量比对还原，
 * 「123456」这类弱口令几秒就能反查出来。本实现用 HMAC-SHA256 加每生独立的随机盐。</p>
 *
 * <p><b>为什么不直接上 BCrypt</b>：本仓未引入 {@code spring-security-crypto}，
 * 依赖树里没有 BCrypt 实现。为一个口令算法新增一整个安全模块不划算，
 * 而 HMAC-SHA256 在「高熵随机盐 + 不落明文」的前提下已能挡住彩虹表与批量爆破；
 * 若后续真的引入 BCrypt，只需替换本类，调用方无需改动。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class StudentPasswordSupport {

    /**
     * 口令最小长度
     */
    private static final int MIN_LENGTH = 6;

    /**
     * 口令最大长度：口令会作为 HMAC 的输入，超长没有收益，只放大报文
     */
    private static final int MAX_LENGTH = 32;

    /**
     * 盐的十六进制长度（16 字节）
     */
    private static final int SALT_BYTES = 16;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private StudentPasswordSupport() {
    }

    /**
     * 生成一个新盐。
     *
     * <p>用 {@link SecureRandom} 而非 {@code Random}：后者是线性同余，
     * 观察若干次生成的盐即可推算后续序列，等于没有盐。</p>
     *
     * @return 32 位十六进制盐串
     */
    public static String newSalt() {
        var bytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(bytes);
        var chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX[v >>> 4];
            chars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * 计算口令摘要。
     *
     * @param rawPassword 明文口令
     * @param salt        盐
     * @return 十六进制摘要；盐为空时返回 {@code null}（无盐不提供弱化版摘要）
     */
    public static String hash(String rawPassword, String salt) {
        if (StringUtils.isBlank(rawPassword) || StringUtils.isBlank(salt)) {
            return null;
        }
        return DigestUtils.hmacSha256(rawPassword, salt);
    }

    /**
     * 校验口令是否与库中摘要匹配。
     *
     * <p>用定长比较而非 {@code String.equals}：后者按字符逐位提前返回，
     * 比较耗时会泄漏「前几位猜对了」，可被用来逐字节爆破摘要。</p>
     *
     * @param rawPassword  明文口令
     * @param salt         库中盐
     * @param expectedHash 库中摘要
     * @return 是否匹配；任一入参为空一律返回 {@code false}
     */
    public static boolean matches(String rawPassword, String salt, String expectedHash) {
        var actual = hash(rawPassword, salt);
        if (actual == null || StringUtils.isBlank(expectedHash)) {
            return false;
        }
        return constantTimeEquals(actual, expectedHash);
    }

    /**
     * 校验口令强度。
     *
     * @param rawPassword 明文口令
     * @throws BusinessException 口令为空或长度越界时抛出，提示文案面向管理员
     */
    public static void validate(String rawPassword) {
        if (StringUtils.isBlank(rawPassword)) {
            throw new BusinessException("登录密码不能为空");
        }
        int length = rawPassword.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new BusinessException("登录密码长度需为 " + MIN_LENGTH + " 到 " + MAX_LENGTH + " 位");
        }
    }

    /**
     * 定长字符串比较：用异或累积差异，避免提前返回泄漏前缀长度。
     *
     * @param left  左侧字符串
     * @param right 右侧字符串
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
