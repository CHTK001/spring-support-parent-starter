package com.chua.starter.sync.support.security;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.sync.support.properties.SyncProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 同步消息传输加密工具。
 * <p>
 * 基于 AES-GCM 认证加密，密钥由配置的加密密钥经 SHA-256 派生。
 * 仅加密消息 payload（topic 不加密），使用方无感知。
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class SyncCryptoUtils {

    /**
     * 加密标记字段
     */
    public static final String ENCRYPTED = "encrypted";

    /**
     * 加密算法字段
     */
    public static final String ALGORITHM = "encAlgorithm";

    /**
     * 密文字段
     */
    public static final String CIPHER = "encData";

    /**
     * AES-GCM 变换名
     */
    private static final String AES_GCM = "AES/GCM/NoPadding";

    /**
     * AES 算法名
     */
    private static final String AES_ALGORITHM = "AES";

    /**
     * SHA-256 摘要算法名
     */
    private static final String SHA_256 = "SHA-256";

    /**
     * GCM 初始向量长度（字节）
     */
    private static final int GCM_IV_LENGTH = 12;

    /**
     * GCM 认证标签长度（比特）
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * 工具类构造器，禁止实例化。
     */
    private SyncCryptoUtils() {
    }

    /**
     * 判断是否已启用加密（配置了密钥）
     *
     * @param properties 同步配置
     * @return true=启用
     */
    public static boolean isEnabled(SyncProperties properties) {
        return properties != null && StringUtils.isNotBlank(properties.getCryptoSecret());
    }

    /**
     * 加密消息 payload。未启用加密或加密失败时原样返回。
     *
     * @param payload    明文消息（Map 或其它可 JSON 序列化对象）
     * @param properties 同步配置
     * @return 加密后的包装 Map，未启用时返回原对象
     */
    public static Object encrypt(Object payload, SyncProperties properties) {
        if (!isEnabled(properties) || payload == null) {
            return payload;
        }
        try {
            String plainJson = Json.toJson(payload);
            byte[] key = deriveKey(properties.getCryptoSecret());
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, AES_ALGORITHM), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> wrapper = new HashMap<>(4);
            wrapper.put(ENCRYPTED, true);
            wrapper.put(ALGORITHM, AES_GCM);
            wrapper.put(CIPHER, Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipherBytes));
            return wrapper;
        } catch (Exception e) {
            return payload;
        }
    }

    /**
     * 解密消息 payload。非加密消息、未配置密钥或解密失败时原样返回。
     *
     * @param message    收到的消息对象
     * @param properties 同步配置
     * @return 解密后的 Map，未启用或不匹配时返回原对象
     */
    public static Object decrypt(Object message, SyncProperties properties) {
        if (!isEnabled(properties) || message == null || !(message instanceof Map<?, ?> map)) {
            return message;
        }
        if (!Boolean.TRUE.equals(map.get(ENCRYPTED))) {
            return message;
        }
        try {
            String encoded = String.valueOf(map.get(CIPHER));
            int sep = encoded.indexOf(':');
            if (sep <= 0) {
                return message;
            }
            byte[] iv = Base64.getDecoder().decode(encoded.substring(0, sep));
            byte[] cipherBytes = Base64.getDecoder().decode(encoded.substring(sep + 1));

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(properties.getCryptoSecret()), AES_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return Json.fromJson(new String(plainBytes, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return message;
        }
    }

    /**
     * 由加密密钥派生 AES 密钥。
     *
     * @param secret 配置的加密密钥
     * @return SHA-256 派生的 32 字节密钥
     * @throws Exception SHA-256 算法不可用时抛出
     */
    private static byte[] deriveKey(String secret) throws Exception {
        return MessageDigest.getInstance(SHA_256).digest(secret.getBytes(StandardCharsets.UTF_8));
    }
}
