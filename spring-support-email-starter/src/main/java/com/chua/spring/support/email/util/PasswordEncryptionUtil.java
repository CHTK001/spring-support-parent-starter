package com.chua.spring.support.email.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 密码加密工具
 * 
 * @author CH
 */
@Slf4j
public class PasswordEncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // 默认密钥（生产环境应从配置文件读取）
    private static final String DEFAULT_SECRET_KEY = "EmailSystemSecretKey2026!@#$%^";

    private static SecretKey secretKey;

    static {
        try {
            // 使用默认密钥初始化
            byte[] keyBytes = DEFAULT_SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] key = new byte[32]; // 256 bits
            System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
            secretKey = new SecretKeySpec(key, ALGORITHM);
        } catch (Exception e) {
            log.error("初始化加密密钥失败", e);
        }
    }

    /**
     * 生成新的 AES 密钥
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        return keyGenerator.generateKey();
    }

    /**
     * 设置自定义密钥
     */
    public static void setSecretKey(String key) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] keyArray = new byte[32];
            System.arraycopy(keyBytes, 0, keyArray, 0, Math.min(keyBytes.length, 32));
            secretKey = new SecretKeySpec(keyArray, ALGORITHM);
        } catch (Exception e) {
            log.error("设置密钥失败", e);
        }
    }

    /**
     * 加密密码
     */
    public static String encrypt(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }

        try {
            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // 初始化 Cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // 加密
            byte[] encryptedBytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

            // 组合 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Base64 编码
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("加密密码失败", e);
            throw new RuntimeException("加密失败: " + e.getMessage());
        }
    }

    /**
     * 解密密码
     */
    public static String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return null;
        }

        try {
            // Base64 解码
            byte[] decoded = Base64.getDecoder().decode(encryptedPassword);

            // 提取 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            // 初始化 Cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // 解密
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密密码失败", e);
            throw new RuntimeException("解密失败: " + e.getMessage());
        }
    }

    /**
     * 验证密码是否正确
     */
    public static boolean verify(String password, String encryptedPassword) {
        try {
            String decrypted = decrypt(encryptedPassword);
            return password.equals(decrypted);
        } catch (Exception e) {
            return false;
        }
    }
}
