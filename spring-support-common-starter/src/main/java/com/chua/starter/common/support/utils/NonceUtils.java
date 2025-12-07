package com.chua.starter.common.support.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Nonce解析工具�?
 *
 * @author CH
 * @since 2025/01/15
 */
@Slf4j
public class NonceUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 解析并验证Nonce
     *
     * @param nonceStr 前端传递的nonce字符�?
     * @param sign     前端传递的签名
     * @param maxAge   最大有效时间（毫秒），默认5分钟
     * @return 验证结果
     */
    public static NonceValidationResult validateNonce(String nonceStr, String sign, long maxAge) {
        if (!StringUtils.hasText(nonceStr) || !StringUtils.hasText(sign)) {
            return new NonceValidationResult(false, 0, sign, nonceStr);
        }

        try {
            // 验证nonce格式（简单验证长度和基本字符�?
            if (nonceStr.length() < 32) {
                log.warn("Nonce长度不足: {}", nonceStr);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }

            // 验证签名格式
            if (!isValidSignFormat(sign)) {
                log.warn("签名格式不正�? {}", sign);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }

            // 提取时间戳（这里简化处理，实际应该有更复杂的解析逻辑�?
            long timestamp = extractTimestamp(nonceStr);
            if (timestamp <= 0) {
                log.warn("无法提取有效时间�? {}", nonceStr);
                return new NonceValidationResult(false, 0, sign, nonceStr);
            }

            // 验证时间戳有效�?
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - timestamp) > maxAge) {
                log.warn("Nonce已过�? 当前时间={}, nonce时间={}, 差�?{}ms", currentTime, timestamp,
                        Math.abs(currentTime - timestamp));
                return new NonceValidationResult(false, timestamp, sign, nonceStr);
            }

            return new NonceValidationResult(true, timestamp, sign, nonceStr);
        } catch (Exception e) {
            log.error("解析Nonce时发生异�? nonce={}, sign={}", nonceStr, sign, e);
            return new NonceValidationResult(false, 0, sign, nonceStr);
        }
    }

    /**
     * 解析并验证Nonce（使用默�?分钟有效期）
     *
     * @param nonceStr 前端传递的nonce字符�?
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
        // 简单验证签名格式（32位十六进制字符串�?
        return Pattern.matches("^[a-fA-F0-9]{32}$", sign);
    }

    /**
     * 从nonce中提取时间戳（简化实现）
     *
     * @param nonceStr nonce字符�?
     * @return 时间�?
     */
    private static long extractTimestamp(String nonceStr) {
        try {
            // 简化实现：假设时间戳在nonce的前13位是时间�?
            // 实际实现应该根据前端nonce生成规则来解�?
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
            log.warn("提取时间戳失�? {}", nonceStr, e);
            return System.currentTimeMillis();
        }
    }

    /**
     * 验证签名（基于请求参数）
     *
     * @param request HttpServletRequest对象
     * @param sign    前端传递的签名
     * @return 是否验证通过
     */
    public static boolean verifySign(HttpServletRequest request, String sign) {
        try {
            // 获取nonce和timestamp
            String nonce = request.getHeader("x-nonce");
            String timestampStr = request.getHeader("x-timestamp");

            if (!StringUtils.hasText(nonce) || !StringUtils.hasText(timestampStr) || !StringUtils.hasText(sign)) {
                return false;
            }

            long timestamp = Long.parseLong(timestampStr);

            // 生成期望的签�?
            String expectedSign = generateSign(request, timestamp);
            return expectedSign.equalsIgnoreCase(sign);
        } catch (Exception e) {
            log.error("验证签名时发生异�?, e);
            return false;
        }
    }

    /**
     * 生成签名（基于请求参数）
     *
     * @param request   HttpServletRequest对象
     * @param timestamp 时间�?
     * @return 生成的签�?
     */
    public static String generateSign(HttpServletRequest request, long timestamp) {
        try {
            // 收集请求参数
            Map<String, Object> params = new TreeMap<>();

            // 收集URL参数
            Map<String, String[]> parameterMap = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    params.put(key, values.length == 1 ? values[0] : Arrays.toString(values));
                }
            }

            // 收集请求体数据（对于JSON请求�?
            String contentType = request.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                // 注意：这里简化处理，实际项目中可能需要从请求体缓存中读取
                // 因为HttpServletRequest的输入流只能读取一�?
            }

            // 添加nonce和timestamp
            params.put("_nonce", request.getHeader("x-nonce"));
            params.put("_timestamp", timestamp);

            // 按键名排序并拼接参数
            StringBuilder paramString = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    paramString.append(entry.getKey()).append("=").append(value).append("&");
                }
            }

            // 移除末尾�?符号
            if (paramString.length() > 0 && paramString.charAt(paramString.length() - 1) == '&') {
                paramString.setLength(paramString.length() - 1);
            }

            // 添加密钥
            String secretKey = getSecretKey();
            String dataToSign = paramString.toString() + secretKey;

            // 生成MD5签名
            return md5Hash(dataToSign);
        } catch (Exception e) {
            log.error("生成签名时发生异�?, e);
            return "";
        }
    }

    /**
     * 生成签名（示例实现）
     *
     * @param nonceStr  nonce字符�?
     * @param timestamp 时间�?
     * @return 生成的签�?
     */
    public static String generateSign(String nonceStr, long timestamp) {
        // 使用更安全的签名生成逻辑
        String data = nonceStr + timestamp + getSecretKey(); // 这里的secretKey应该是配置的密钥
        return md5Hash(data);
    }

    /**
     * 生成复杂的Nonce（与前端对应�?
     *
     * @return 生成的nonce
     */
    public static String generateNonce() {
        long timestamp = System.currentTimeMillis();

        // 生成多个随机�?
        String random1 = generateRandomString(5);
        String random2 = generateRandomString(7);
        String random3 = generateRandomString(6);

        // 生成基于时间戳的哈希-like�?
        long timeHash = (timestamp * 9301 + 49297) % 233280;

        // 生成序列�?
        long sequence = (timestamp & 0xFFFF) ^ (timestamp >>> 16);

        // 生成基于随机数的混合�?
        long mixed = ((random1.length() * random2.length() * random3.length()) + timestamp) % 999999;

        // 添加UUID的部分作为额外的随机因子
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 添加安全随机�?
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

        // 如果太长则截�?
        if (nonce.length() > 128) {
            nonce = nonce.substring(0, 128);
        }

        return nonce;
    }

    /**
     * 生成指定长度的随机字符串
     *
     * @param length 长度
     * @return 随机字符�?
     */
    private static String generateRandomString(int length) {
        byte[] randomBytes = new byte[length];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes)
                .replaceAll("[^a-zA-Z0-9]", "")
                .substring(0, Math.min(length, 20)); // 限制长度
    }

    /**
     * 获取密钥（实际应该从配置中获取）
     *
     * @return 密钥
     */
    private static String getSecretKey() {
        // 实际项目中应该从配置文件或环境变量中获取
        return "your-secret-key-here";
    }

    /**
     * MD5哈希函数
     *
     * @param input 输入字符�?
     * @return MD5哈希�?
     */
    private static String md5Hash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // 出错时返回简单哈�?
            return Integer.toString(input.hashCode(), 16);
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
