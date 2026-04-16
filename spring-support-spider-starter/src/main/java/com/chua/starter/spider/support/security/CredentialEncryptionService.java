package com.chua.starter.spider.support.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 凭证 AES 加密/解密服务。
 *
 * <p>使用 AES/GCM/NoPadding 对凭证数据进行加密存储，密钥从配置属性
 * {@code plugin.spider.credential.aes-key} 读取。若未配置，则自动生成随机密钥并记录警告。</p>
 *
 * <p>加密格式：Base64(IV[12字节] + 密文 + GCM认证标签[16字节])</p>
 *
 * @author CH
 */
@Slf4j
@Service
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;

    private final SecretKey secretKey;

    public CredentialEncryptionService(
            @Value("${plugin.spider.credential.aes-key:}") String aesKey) {
        this.secretKey = resolveKey(aesKey);
    }

    /**
     * AES/GCM 加密明文，返回 Base64 编码的密文（含 IV 前缀）。
     *
     * @param plaintext 待加密的明文字符串
     * @return Base64 编码的密文
     * @throws IllegalArgumentException 若 plaintext 为 null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 拼接 IV + 密文
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    /**
     * Base64 解码后 AES/GCM 解密，返回原始明文。
     *
     * @param ciphertext Base64 编码的密文（含 IV 前缀）
     * @return 解密后的明文字符串
     * @throws IllegalArgumentException 若 ciphertext 为 null 或格式非法
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("ciphertext must not be null");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid ciphertext: too short");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(encryptedBytes);

            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("AES decryption failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SecretKey resolveKey(String aesKey) {
        if (aesKey == null || aesKey.isBlank()) {
            log.warn("[Spider] plugin.spider.credential.aes-key 未配置，已自动生成随机 AES 密钥。" +
                    "重启后历史加密数据将无法解密，请在生产环境中配置固定密钥。");
            return generateRandomKey();
        }
        // 将配置的字符串派生为 AES 密钥（取 UTF-8 字节，不足/超出 32 字节则填充/截断）
        byte[] keyBytes = deriveKeyBytes(aesKey);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private SecretKey generateRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, new SecureRandom());
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate random AES key", e);
        }
    }

    private byte[] deriveKeyBytes(String key) {
        byte[] raw = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] keyBytes = new byte[32]; // AES-256
        System.arraycopy(raw, 0, keyBytes, 0, Math.min(raw.length, keyBytes.length));
        return keyBytes;
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
