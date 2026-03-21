package com.chua.payment.support.configuration;

import com.chua.payment.support.exception.PaymentException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 支付敏感字段加解密服务。
 * 支持新配置密钥，同时兼容历史固定密钥，避免旧数据无法解密。
 */
@Component
public class PaymentCipherService {

    static final String LEGACY_DEFAULT_KEY = "PaymentSystem16";

    private final Environment environment;

    public PaymentCipherService(Environment environment) {
        this.environment = environment;
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        return doCipher(plainText, requiredEncryptionKey(), Cipher.ENCRYPT_MODE);
    }

    public String decrypt(String encryptedText) {
        if (!StringUtils.hasText(encryptedText)) {
            return encryptedText;
        }
        PaymentException lastException = null;
        for (String key : candidateDecryptionKeys()) {
            try {
                return doCipher(encryptedText, key, Cipher.DECRYPT_MODE);
            } catch (PaymentException e) {
                lastException = e;
            }
        }
        throw new PaymentException("解密失败，请检查 payment/encryption 或 plugin.payment.security 密钥配置", lastException);
    }

    List<String> candidateDecryptionKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (StringUtils.hasText(configuredEncryptionKey())) {
            keys.add(configuredEncryptionKey());
        }
        keys.addAll(splitKeys(property(
                "plugin.payment.security.legacy-keys",
                "plugin.payment.security.legacyKeys",
                "payment.encryption.legacy-keys",
                "payment.encryption.legacyKeys")));
        keys.add(LEGACY_DEFAULT_KEY);
        return new ArrayList<>(keys);
    }

    String requiredEncryptionKey() {
        String key = configuredEncryptionKey();
        return StringUtils.hasText(key) ? key : LEGACY_DEFAULT_KEY;
    }

    private String configuredEncryptionKey() {
        return property(
                "plugin.payment.security.key",
                "plugin.payment.security.encryption-key",
                "plugin.payment.security.encryptionKey",
                "payment.encryption.key",
                "payment.encryption.encryption-key",
                "payment.encryption.encryptionKey");
    }

    private String property(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<String> splitKeys(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                keys.add(item.trim());
            }
        }
        return keys;
    }

    private String doCipher(String value, String keyText, int mode) {
        try {
            SecretKeySpec key = new SecretKeySpec(normalizeKey(keyText), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(mode, key);
            if (mode == Cipher.ENCRYPT_MODE) {
                byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(encrypted);
            }
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(value));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PaymentException(mode == Cipher.ENCRYPT_MODE ? "加密失败" : "解密失败", e);
        }
    }

    private byte[] normalizeKey(String keyText) {
        if (!StringUtils.hasText(keyText)) {
            throw new PaymentException("支付加密密钥不能为空");
        }
        byte[] source = keyText.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[16];
        int length = Math.min(source.length, normalized.length);
        System.arraycopy(source, 0, normalized, 0, length);
        return normalized;
    }
}
