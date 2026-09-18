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
* 支付敏感字段加解密服务，支持多密钥兼容（新旧密钥均能解密历史数据）。
*
* @author CH
* @since 4.0.0.50
 */
@Component
public class PaymentCipherService {

    static final String LEGACY_DEFAULT_KEY = "PaymentSystem16"; // legacy默认Key

    /** 环境 */
    private final Environment environment;

    /**
    * 创建 薪酬cipher服务 实例
    * @param environment 环境
    */
    public PaymentCipherService(Environment environment) {
        this.environment = environment;
    }

    /**
    * Encrypt
    *
    * @param plainText plain文本
    * @return encrypt的结果
    */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        return doCipher(plainText, requiredEncryptionKey(), Cipher.ENCRYPT_MODE);
    }

    /**
    * Decrypt
    *
    * @param encryptedText encrypted文本
    * @return decrypt的结果
    */
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
        /**
        * 薪酬异常。
        * @param 密钥配置" 密钥配置"
        * @param lastException 最后一个异常
        * @return 薪酬异常的结果
        */
        throw new PaymentException("解密失败，请检查 payment/encryption 或 plugin.payment.security 密钥配置", lastException);
    }

    /**
    * candidate解密Key。
    * @return candidate解密Key的结果
    */
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

    /**
    * required加密Key。
    * @return required加密Key的结果
    */
    String requiredEncryptionKey() {
        String key = configuredEncryptionKey();
        return StringUtils.hasText(key) ? key : LEGACY_DEFAULT_KEY;
    }

    /**
    * configured加密Key
    *
    * @return configured加密Key的结果
    */
    private String configuredEncryptionKey() {
        return property(
                "plugin.payment.security.key",
                "plugin.payment.security.encryption-key",
                "plugin.payment.security.encryptionKey",
                "payment.encryption.key",
                "payment.encryption.encryption-key",
                "payment.encryption.encryptionKey");
    }

    /**
    * 财产
    *
    * @param keys Key
    * @return 财产的结果
    */
    private String property(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
    * 分割Key
    *
    * @param value 值
    * @return 分割Key的结果
    */
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

    /**
    * 执行cipher
    *
    * @param value 值
    * @param keyText Key文本
    * @param mode mode
    * @return 执行cipher的结果
    */
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
            /**
            * 字符串。
            * @param decrypted decrypted
            * @param StandardCharsets.UTF_8 标准字符集.utf8
            * @return 字符串的结果
            */
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            /**
            * 薪酬异常。
            * @param "解密失败" "解密失败"
            * @param e e
            * @return 薪酬异常的结果
            */
            throw new PaymentException(mode == Cipher.ENCRYPT_MODE ? "加密失败" : "解密失败", e);
        }
    }

    /**
    * normalizeKey
    *
    * @param keyText Key文本
    * @return normalizeKey的结果
    */
    private byte[] normalizeKey(String keyText) {
        if (!StringUtils.hasText(keyText)) {
            /**
            * 薪酬异常。
            * @param "支付加密密钥不能为空" "支付加密密钥不能为空"
            * @return 薪酬异常的结果
            */
            throw new PaymentException("支付加密密钥不能为空");
        }
        byte[] source = keyText.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[16];
        int length = Math.min(source.length, normalized.length);
        System.arraycopy(source, 0, normalized, 0, length);
        return normalized;
    }
}

