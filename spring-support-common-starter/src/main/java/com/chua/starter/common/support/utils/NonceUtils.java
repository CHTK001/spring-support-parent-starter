package com.chua.starter.common.support.utils;

import com.chua.starter.common.support.properties.NonceSignProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Nonce 与签名验证工具类
 * <p>
 * 前端算法：sign = MD5(nonce + fingerprint + timestamp + paramsMd5 + secretKey)
 * paramsMd5 = MD5(请求参数自然排序、排除 file、key=value& 拼接)
 * </p>
 * <p>
 * 防重放：x-nonce 仅可使用一次，已使用的 nonce 会被缓存并在有效期内拒绝
 * </p>
 *
 * @author CH
 * @since 2025/01/15
 */
@Slf4j
public class NonceUtils {

    /** 请求签名已在前置过滤器通过校验的标记 */
    public static final String REQUEST_SIGN_VALIDATED_ATTRIBUTE =
            NonceUtils.class.getName() + ".requestSignValidated";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 排除的参数字段（如 file 等） */
    private static final Set<String> EXCLUDED_PARAM_KEYS = Set.of("file", "files");

    /** 签名密钥，可通过 setSecretKey 或配置注入，默认与 NonceSignProperties.DEFAULT_SECRET 保持一致 */
    private static volatile String secretKey = NonceSignProperties.DEFAULT_SECRET;

    /** 默认有效期 5 分钟 */
    private static final long DEFAULT_MAX_AGE_MS = 5 * 60 * 1000L;

    /** 已使用的 nonce 缓存，用于防 XHR 重放（TTL 与 timestamp 有效期一致） */
    private static final Cache<String, Boolean> USED_NONCE_CACHE = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(DEFAULT_MAX_AGE_MS, TimeUnit.MILLISECONDS)
            .build();

    /**
     * 解析并验证 Nonce（使用 x-timestamp 头）
     *
     * @param request 请求（需包含 x-nonce、x-sign、x-timestamp）
     * @param maxAge  最大有效时间（毫秒）
     * @return 验证结果
     */
    public static NonceValidationResult validateNonce(HttpServletRequest request, long maxAge) {
        String nonceStr = request.getHeader("x-nonce");
        String sign = request.getHeader("x-sign");
        String timestampStr = request.getHeader("x-timestamp");
        if (!StringUtils.hasText(nonceStr) || !StringUtils.hasText(sign) || !StringUtils.hasText(timestampStr)) {
            return new NonceValidationResult(false, 0, sign != null ? sign : "", nonceStr != null ? nonceStr : "");
        }
        try {
            long timestamp = Long.parseLong(timestampStr);
            if (nonceStr.length() < 32) {
                log.warn("Nonce 长度不足: {}", nonceStr);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }
            if (!isValidSignFormat(sign)) {
                log.warn("签名格式不正确: {}", sign);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - timestamp) > maxAge) {
                log.warn("Nonce 已过期 当前={}, 请求={}, 差距{}ms", currentTime, timestamp, Math.abs(currentTime - timestamp));
                return new NonceValidationResult(false, timestamp, sign, nonceStr);
            }
            return new NonceValidationResult(true, timestamp, sign, nonceStr);
        } catch (Exception e) {
            log.error("解析 Nonce 异常 nonce={}, sign={}", nonceStr, sign, e);
            return new NonceValidationResult(false, 0, sign, nonceStr);
        }
    }

    /**
     * 解析并验证 Nonce（使用默认 5 分钟有效期）
     */
    public static NonceValidationResult validateNonce(HttpServletRequest request) {
        return validateNonce(request, DEFAULT_MAX_AGE_MS);
    }

    /**
     * 解析并验证 Nonce（兼容旧 API，使用 nonceStr 和 sign）
     *
     * @param nonceStr 前端传递的 nonce 字符串
     * @param sign     前端传递的签名
     * @param maxAge   最大有效时间（毫秒）
     * @return 验证结果
     */
    public static NonceValidationResult validateNonce(String nonceStr, String sign, long maxAge) {
        if (!StringUtils.hasText(nonceStr) || !StringUtils.hasText(sign)) {
            return new NonceValidationResult(false, 0, sign, nonceStr);
        }
        try {
            if (nonceStr.length() < 32) {
                log.warn("Nonce 长度不足: {}", nonceStr);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }
            if (!isValidSignFormat(sign)) {
                log.warn("签名格式不正确: {}", sign);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }
            long timestamp = extractTimestamp(nonceStr);
            if (timestamp <= 0) {
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - timestamp) > maxAge) {
                return new NonceValidationResult(false, timestamp, sign, nonceStr);
            }
            return new NonceValidationResult(true, timestamp, sign, nonceStr);
        } catch (Exception e) {
            log.error("解析 Nonce 异常 nonce={}, sign={}", nonceStr, sign, e);
            return new NonceValidationResult(false, 0, sign, nonceStr);
        }
    }

    /**
     * 解析并验证Nonce（使用默认5分钟有效期）
     *
     * @param nonceStr 前端传递的nonce字符串
     * @param sign     前端传递的签名
     * @return 验证结果
     */
    public static NonceValidationResult validateNonce(String nonceStr, String sign) {
        return validateNonce(nonceStr, sign, 5 * 60 * 1000L); // 默认5分钟
    }

    /**
     * 验证签名格式
     *
     * @param sign 签名
     * @return 是否有效格式
     */
    private static boolean isValidSignFormat(String sign) {
        // 简单验证签名格式（32位十六进制字符串）
        return Pattern.matches("^[a-fA-F0-9]{32}$", sign);
    }

    /**
     * 从nonce中提取时间戳（简化实现）
     *
     * @param nonceStr nonce字符串
     * @return 时间戳
     */
    private static long extractTimestamp(String nonceStr) {
        try {
            // 简化实现：假设时间戳在nonce的前13位是时间戳
            // 实际实现应该根据前端nonce生成规则来解析
            if (nonceStr.length() >= 13) {
                String potentialTimestamp = nonceStr.substring(0, 13);
                // 检查是否为数字
                if (potentialTimestamp.matches("\\d{13}")) {
                    return Long.parseLong(potentialTimestamp);
                }
            }

            // 如果无法从字符串中提取，返回当前时间戳（简化处理）
            return System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("提取时间戳失败:  {}", nonceStr, e);
            return System.currentTimeMillis();
        }
    }

    /**
     * 判断请求是否携带完整签名头（有则后端尝试验证）
     *
     * @param request 请求
     * @return 是否包含 x-nonce、x-sign、x-timestamp、x-req-fingerprint
     */
    public static boolean hasSignHeaders(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader("x-nonce"))
                && StringUtils.hasText(request.getHeader("x-sign"))
                && StringUtils.hasText(request.getHeader("x-timestamp"))
                && StringUtils.hasText(request.getHeader("x-req-fingerprint"));
    }

    /**
     * 判断 x-nonce 是否已被使用（防 XHR 重放）
     *
     * @param nonce x-nonce 值
     * @return true 表示已使用（重放），false 表示未使用
     */
    public static boolean isNonceReplayed(String nonce) {
        if (!StringUtils.hasText(nonce)) {
            return true;
        }
        return USED_NONCE_CACHE.getIfPresent(nonce) != null;
    }

    /**
     * 记录已使用的 nonce（验证通过后调用，用于防重放）
     *
     * @param nonce    x-nonce 值
     * @param expireAt 过期时间戳（毫秒）
     */
    public static void recordNonce(String nonce, long expireAt) {
        if (StringUtils.hasText(nonce)) {
            USED_NONCE_CACHE.put(nonce, Boolean.TRUE);
        }
    }

    /**
     * 完整校验 XHR 请求（时间戳 + x-nonce 防重放 + 签名）
     * 通过后自动记录 nonce，防止重放
     *
     * @param request 请求
     * @return true 校验通过，false 校验失败
     */
    public static boolean validateXhrRequest(HttpServletRequest request) {
        if (!hasSignHeaders(request)) {
            return false;
        }
        var nonce = request.getHeader("x-nonce");
        var sign = request.getHeader("x-sign");
        var result = validateNonce(request);
        if (!result.isValid()) {
            return false;
        }
        if (isNonceReplayed(nonce)) {
            log.warn("[Nonce][防重放] x-nonce 已使用，疑似重放攻击 nonce={}", nonce);
            return false;
        }
        if (!verifySign(request, sign)) {
            return false;
        }
        recordNonce(nonce, result.getTimestamp() + DEFAULT_MAX_AGE_MS);
        return true;
    }

    /**
     * 验证签名（仅当 hasSignHeaders 为 true 时调用）
     * 算法：sign = MD5(nonce + fingerprint + timestamp + paramsMd5 + secretKey)
     *
     * @param request HttpServletRequest
     * @param sign    前端传递的签名
     * @return 是否验证通过
     */
    public static boolean verifySign(HttpServletRequest request, String sign) {
        if (!hasSignHeaders(request)) {
            return false;
        }
        try {
            String nonce = request.getHeader("x-nonce");
            String fingerprint = request.getHeader("x-req-fingerprint");
            String timestampStr = request.getHeader("x-timestamp");
            long timestamp = Long.parseLong(timestampStr);
            String expectedSign = generateSign(request, nonce, fingerprint, timestamp);
            return expectedSign.equalsIgnoreCase(sign);
        } catch (Exception e) {
            log.error("验证签名时发生异常", e);
            return false;
        }
    }

    /**
     * 生成签名（与前端算法一致）
     * paramsMd5 = MD5(请求参数自然排序、排除 file、key=value& 拼接)
     * sign = MD5(nonce + fingerprint + timestamp + paramsMd5 + secretKey)
     *
     * @param request    HttpServletRequest
     * @param nonce      x-nonce
     * @param fingerprint x-req-fingerprint
     * @param timestamp  时间戳
     * @return 期望的签名
     */
    public static String generateSign(HttpServletRequest request, String nonce, String fingerprint, long timestamp) {
        try {
            Map<String, String> filteredParams = collectParams(request);
            List<String> pairs = new ArrayList<>();
            for (String key : new TreeSet<>(filteredParams.keySet())) {
                pairs.add(key + "=" + filteredParams.get(key));
            }
            String paramsString = String.join("&", pairs);
            String paramsMd5 = md5Hash(paramsString);
            String sk = secretKey != null ? secretKey : NonceSignProperties.DEFAULT_SECRET;
            String signInput = nonce + fingerprint + timestamp + paramsMd5 + sk;
            return md5Hash(signInput);
        } catch (Exception e) {
            log.error("生成签名时发生异常", e);
            return "";
        }
    }

    /**
     * 收集请求参数（排除 file/files，自然排序）
     */
    private static Map<String, String> collectParams(HttpServletRequest request) {
        Map<String, String> result = new TreeMap<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            if (EXCLUDED_PARAM_KEYS.contains(key.toLowerCase())) {
                continue;
            }
            String[] values = entry.getValue();
            if (values != null && values.length > 0) {
                result.put(key, values.length == 1 ? values[0] : String.join(",", values));
            }
        }
        return result;
    }

    /**
     * 设置签名密钥（与前端 secretKey 一致）
     *
     * @param key 密钥
     */
    public static void setSecretKey(String key) {
        secretKey = key;
    }

    /**
     * 生成复杂的Nonce（与前端对应）
     *
     * @return 生成的nonce
     */
    public static String generateNonce() {
        long timestamp = System.currentTimeMillis();

        // 生成多个随机数
        String random1 = generateRandomString(5);
        String random2 = generateRandomString(7);
        String random3 = generateRandomString(6);

        // 生成基于时间戳的哈希-like值
        long timeHash = (timestamp * 9301 + 49297) % 233280;

        // 生成序列值
        long sequence = (timestamp & 0xFFFF) ^ (timestamp >>> 16);

        // 生成基于随机数的混合值
        long mixed = ((random1.length() * random2.length() * random3.length()) + timestamp) % 999999;

        // 添加UUID的部分作为额外的随机因子
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 添加安全随机数
        long secureRandom = SECURE_RANDOM.nextLong();

        // 生成最终的复杂nonce
        String nonce = random1 +
                Long.toString(sequence, 36) +
                random2 +
                Long.toString(timeHash, 36) +
                random3 +
                Long.toString(mixed, 36) +
                uuidPart +
                Long.toString(secureRandom, 36);

        // 确保长度足够复杂
        if (nonce.length() < 48) {
            String padding = generateRandomString(48 - nonce.length());
            nonce = nonce + padding;
        }

        // 如果太长则截断
        if (nonce.length() > 128) {
            nonce = nonce.substring(0, 128);
        }

        return nonce;
    }

    /**
     * 生成指定长度的随机字符串
     *
     * @param length 长度
     * @return 随机字符串
     */
    private static String generateRandomString(int length) {
        byte[] randomBytes = new byte[length];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes)
                .replaceAll("[^a-zA-Z0-9]", "")
                .substring(0, Math.min(length, 20)); // 限制长度
    }

    /**
     * MD5 哈希（UTF-8，与前端一致）
     *
     * @param input 输入字符串
     * @return 32 位十六进制
     */
    private static String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("MD5 计算异常", e);
            return "";
        }
    }

    /**
     * Nonce验证结果
     */
    public static class NonceValidationResult {
        private boolean valid;
        private long timestamp;
        private String sign;
        private String nonce;

        public NonceValidationResult(boolean valid, long timestamp, String sign, String nonce) {
            this.valid = valid;
            this.timestamp = timestamp;
            this.sign = sign;
            this.nonce = nonce;
        }

        // Getters
        public boolean isValid() {
            return valid;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSign() {
            return sign;
        }

        public String getNonce() {
            return nonce;
        }
    }
}
