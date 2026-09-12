package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
* 默认 AES 密码解密器
* <p>
* 使用 AES/ECB/PKCS5Padding 算法进行解密。
* 密钥通过配置项 {@code plugin.datasource.enc.password-secret} 指定，
* 如果未配置则使用默认密钥 {@code defaultSecretKey}。
* </p>
*
* <h3>配置示例</h3>
* <pre>{@code
* plugin:
*   datasource:
*     enc:
*       secret-key: mySecretKey12345   # 加密密钥（必须 16/24/32 字节）
*       algorithm: AES                  # 加密算法
*
* # 使用加密密码
* spring.datasource.password: ENC(uX5z1H+z5f3k8L9mW2qR4w==)
* }</pre> * }</pre>
*
* @since 2026/07/14
* @author CH
 */
@Slf4j
public class AesPasswordDecryptor implements PasswordDecryptor {

    /** AES 算法 */
    /** Aes_algorithm */
    private static final String AES_ALGORITHM = "AES";
    /** Cipher_转变 */
    private static final String CIPHER_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /** 默认密钥（32字节，用于没有配置密钥时的兼容） */
    /** 默认_secret_钥匙 */
    private static final byte[] DEFAULT_SECRET_KEY;

    static {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            DEFAULT_SECRET_KEY = sha.digest("spring-support-datasource-default-key".getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            /**
            * 异常入initializer错误。
            * @param available" 可用"
            * @return 异常入initializer错误的结果
             */
            throw new ExceptionInInitializerError("SHA-256 not available");
        }
    }

    /** Secret密钥 */
    private final byte[] secretKey;

    /**
    * 使用默认密钥创建解密器
     */
    public AesPasswordDecryptor() {
        this.secretKey = DEFAULT_SECRET_KEY;
    }

    /**
    * 使用指定密钥创建解密器
    *
    * @param secretKeyBytes 密钥字节数组（支持 16/24/32 字节）
     */
    public AesPasswordDecryptor(byte[] secretKeyBytes) {
        this.secretKey = Arrays.copyOf(secretKeyBytes, 32);
    }

    /**
    * 从字符串密钥创建解密器
    *
    * @param secretKeyStr 密钥字符串（会通过 SHA-256 哈希到 32 字节）
     */
    public AesPasswordDecryptor(String secretKeyStr) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            this.secretKey = sha.digest(secretKeyStr.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            /**
            * illegal参数异常。
            * @param available" 可用"
            * @param e e
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean supports(String algorithm) {
        return algorithm == null || algorithm.isEmpty() || "AES".equalsIgnoreCase(algorithm);
    }

    @Override
    public String decrypt(String encryptedValue) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(decoded);
            /**
            * 字符串。
            * @param decrypted decrypted
            * @param StandardCharsets.UTF_8 标准字符集.utf8
            * @return 字符串的结果
             */
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES 解密失败", e);
            /**
            * illegal参数异常。
            * @param "密码解密失败，请检查密钥配置" "密码解密失败，请检查密钥配置"
            * @param e e
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("密码解密失败，请检查密钥配置", e);
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 100;
    }
}
