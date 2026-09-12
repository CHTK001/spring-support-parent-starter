package com.chua.starter.ai.support.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
* AI 密钥生成工具类，提供 sk- 前缀安全随机密钥生成能力。
*
* <p>生成 48 字符的 URL 安全 Base64 密钥，前缀为 {@code sk-}，
* 适用于 app钥匙、令牌 等场景。</p>
*
* @since 4.0.0.42
* @author CH
 */
public final class KeyGenerator {

    /**
    * 密钥字节长度（36 字节 → 48 基础64 字符）
     */
    private static final int KEY_BYTE_LENGTH = 36;

    /**
    * 密钥前缀
     */
    private static final String KEY_PREFIX = "sk-";

    /**
    * 密钥截取长度（不含前缀）
     */
    private static final int KEY_BODY_LENGTH = 48;

    /**
    * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 创建 钥匙生成器 实例 */
    private KeyGenerator() {
        // 工具类禁止实例化
    }

    /**
    * 生成 sk- 前缀的随机密钥。
    *
    * <p>使用 {@link SecureRandom} 生成 36 字节随机数，
    * 经 URL 安全 基础64 编码后截取前 48 字符，加 {@code sk-} 前缀返回。</p>
    *
    * @return 密钥字符串，格式如 {@code sk-AbCdEfGhIjKlMnOpQrStUvWxYz1234567890}
     */
    public static String generateKey() {
        byte[] bytes = new byte[KEY_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                .replace("+", "-")
                .replace("/", "_")
                .substring(0, KEY_BODY_LENGTH);
    }
}