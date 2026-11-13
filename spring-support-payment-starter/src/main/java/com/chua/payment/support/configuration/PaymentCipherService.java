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
 * <p>统一使用 AES 算法，密文以 Base64 编码存储。
 * 解密时按「当前配置密钥 → 历史密钥列表 → 内置兼容密钥」的顺序逐个尝试，
 * 保证密钥轮换后旧数据依然可读。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
public class PaymentCipherService {

    static final String LEGACY_DEFAULT_KEY = "PaymentSystem16"; // legacy 默认 Key

    /**
     * 应用环境，用于读取密钥配置项。
     */
    private final Environment environment;

    /**
     * 构造支付加解密服务。
     *
     * @param environment 应用环境
     */
    public PaymentCipherService(Environment environment) {
        this.environment = environment;
    }

    /**
     * 对明文进行加密。
     *
     * @param plainText 待加密的明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        return doCipher(plainText, requiredEncryptionKey(), Cipher.ENCRYPT_MODE);
    }

    /**
     * 对密文进行解密。
     *
     * <p>依次尝试所有候选密钥，任一密钥解密成功即返回明文。</p>
     *
     * @param encryptedText 待解密的密文
     * @return 解密后的明文
     * @throws PaymentException 所有候选密钥均无法解密时抛出
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
        throw new PaymentException("解密失败，请检查 payment/encryption 与 plugin.payment.security 密钥配置", lastException);
    }

    /**
     * 构建解密候选密钥列表。
     *
     * <p>依次包含：配置的加密密钥、历史兼容密钥、内置兼容密钥。
     * 使用 {@link LinkedHashSet} 去重并保持优先级顺序。</p>
     *
     * @return 解密候选密钥列表
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
     * 获取本次加密必须使用的密钥。
     *
     * @return 加密密钥值，未配置时返回内置兼容密钥
     */
    String requiredEncryptionKey() {
        String key = configuredEncryptionKey();
        return StringUtils.hasText(key) ? key : LEGACY_DEFAULT_KEY;
    }

    /**
     * 从配置中心读取当前加密密钥。
     *
     * <p>按兼容顺序依次尝试 {@code plugin.payment.security} 与 {@code payment.encryption} 下的多种键名。</p>
     *
     * @return 加密密钥值，未配置时返回 {@code null}
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
     * 按候选键名顺序读取第一个非空配置项。
     *
     * @param keys 候选配置键名
     * @return 属性值，全部未配置时返回 {@code null}
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
     * 按逗号分隔配置项并拆分为多个密钥。
     *
     * @param value 逗号分隔的配置值
     * @return 拆分后的密钥列表
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
     * 执行加解密运算。
     *
     * @param value   待处理的文本
     * @param keyText 密钥文本
     * @param mode    {@link Cipher#ENCRYPT_MODE} 或 {@link Cipher#DECRYPT_MODE}
     * @return 加解密后的文本
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
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PaymentException(mode == Cipher.ENCRYPT_MODE ? "加密失败" : "解密失败", e);
        }
    }

    /**
     * 将密钥文本归一化为 16 字节 AES 密钥。
     *
     * <p>取密钥文本的 UTF-8 字节，不足 16 字节补零，超长则截断。</p>
     *
     * @param keyText 密钥文本
     * @return 16 字节的 AES 密钥
     * @throws PaymentException 密钥文本为空时抛出
     */
    private byte[] normalizeKey(String keyText) {
        if (!StringUtils.hasText(keyText)) {
            throw new PaymentException("支付加密密钥不能为空");
        }
        byte[] source = keyText.getBytes(StandardCharsets.UTF_8);
        // [P3C A20 豁免]
        byte[] normalized = new byte[16];
        int length = Math.min(source.length, normalized.length);
        System.arraycopy(source, 0, normalized, 0, length);
        return normalized;
    }
}
