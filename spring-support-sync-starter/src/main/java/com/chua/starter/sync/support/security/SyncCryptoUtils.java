package com.chua.starter.sync.support.security;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.crypto.Hkdf;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.sync.support.properties.SyncProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 同步消息传输加密工具。
 *
 * <p>基于 AES-256-GCM 认证加密。每条消息生成一次随机盐，密钥由「配置密钥 + 盐」
 * 经 HKDF-SHA256 派生，因此同一进程发出的任意两条消息都不会共用密钥；配置密钥
 * 本身既不上线路，也不直接当 AES 密钥使用。</p>
 *
 * <p>仅加密消息 payload（topic 不加密），使用方无感知。信封字段：
 * {@code encrypted}、{@code encAlgorithm}、{@code encData}，
 * v2 的 {@code encData} 为 {@code base64(盐):base64(IV):base64(密文)}。</p>
 *
 * <p><b>失败不降级</b>：加密失败抛异常，而不是把明文发出去；解密失败同样抛异常，
 * 由调用方丢弃该消息，绝不会把密文当明文交给业务。旧版本（v1，两段式
 * {@code base64(IV):base64(密文)}）仍可读，便于滚动升级期间兼容。</p>
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
     * 当前算法标识
     */
    public static final String ALGORITHM_V2 = "AES-256-GCM/HKDF-SHA256/v2";

    /**
     * 旧算法标识
     */
    public static final String ALGORITHM_V1 = "AES/GCM/NoPadding";

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
     * HKDF 上下文标签
     */
    private static final String HKDF_INFO = "chua/sync/v2/payload";

    /**
     * 附加认证数据
     */
    private static final byte[] AAD = "chua/sync/v2".getBytes(StandardCharsets.UTF_8);

    /**
     * 每消息随机盐长度（字节）
     */
    private static final int SALT_LENGTH = 16;

    /**
     * 子密钥长度（字节）
     */
    private static final int KEY_LENGTH = 32;

    /**
     * GCM 初始向量长度（字节）
     */
    private static final int GCM_IV_LENGTH = 12;

    /**
     * GCM 认证标签长度（比特）
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * v2 密文段数
     */
    private static final int V2_SEGMENTS = 3;

    /**
     * v1 密文段数
     */
    private static final int V1_SEGMENTS = 2;

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
     * 加密消息 payload。
     *
     * <p>已启用加密时不会返回明文：任何失败都以异常终止本次发送。</p>
     *
     * @param payload    明文消息（Map 或其它可 JSON 序列化对象）
     * @param properties 同步配置
     * @return 加密后的包装 Map，未启用时返回原对象
     * @throws IllegalStateException 加密失败（不会降级为明文）
     */
    public static Object encrypt(Object payload, SyncProperties properties) {
        if (!isEnabled(properties) || payload == null) {
            return payload;
        }
        try {
            if (resolveVersion(properties) <= 1) {
                return encryptLegacy(payload, properties);
            }
            return encryptV2(payload, properties);
        } catch (Exception e) {
            throw new IllegalStateException("同步消息加密失败（不会降级为明文）: " + e.getMessage(), e);
        }
    }

    /**
     * 按 v2 加密：每条消息随机盐，HKDF 派生独立密钥。
     *
     * @param payload    明文消息
     * @param properties 同步配置
     * @return 信封
     * @throws Exception 加密失败
     */
    private static Object encryptV2(Object payload, SyncProperties properties) throws Exception {
        byte[] key = null;
        try {
            byte[] salt = Hkdf.randomBytes(SALT_LENGTH);
            byte[] iv = Hkdf.randomBytes(GCM_IV_LENGTH);
            key = Hkdf.derive(properties.getCryptoSecret().getBytes(StandardCharsets.UTF_8),
                    salt, HKDF_INFO, KEY_LENGTH);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, AES_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(AAD);
            byte[] cipherBytes = cipher.doFinal(Json.toJson(payload).getBytes(StandardCharsets.UTF_8));

            Map<String, Object> wrapper = new HashMap<>(4);
            wrapper.put(ENCRYPTED, true);
            wrapper.put(ALGORITHM, ALGORITHM_V2);
            wrapper.put(CIPHER, Base64.getEncoder().encodeToString(salt)
                    + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(cipherBytes));
            return wrapper;
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    /**
     * 按 v1（旧版）加密：固定密钥 + 随机 IV，仅用于滚动升级过渡。
     *
     * @param payload    明文消息
     * @param properties 同步配置
     * @return 信封
     * @throws Exception 加密失败
     */
    private static Object encryptLegacy(Object payload, SyncProperties properties) throws Exception {
        byte[] key = java.security.MessageDigest.getInstance(SHA_256)
                .digest(properties.getCryptoSecret().getBytes(StandardCharsets.UTF_8));
        try {
            byte[] iv = Hkdf.randomBytes(GCM_IV_LENGTH);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, AES_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(Json.toJson(payload).getBytes(StandardCharsets.UTF_8));

            Map<String, Object> wrapper = new HashMap<>(4);
            wrapper.put(ENCRYPTED, true);
            wrapper.put(ALGORITHM, ALGORITHM_V1);
            wrapper.put(CIPHER, Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(cipherBytes));
            return wrapper;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * 解析加密版本，空值按 2 处理。
     *
     * @param properties 同步配置
     * @return 1 或 2
     */
    private static int resolveVersion(SyncProperties properties) {
        Integer version = properties.getCryptoVersion();
        return version == null || version >= 2 ? 2 : 1;
    }

    /**
     * 解密消息 payload。非加密消息或未启用加密时原样返回。
     *
     * <p>标记为加密但解不开时抛异常，由调用方丢弃该消息；不会把密文当明文交给业务。</p>
     *
     * @param message    收到的消息对象
     * @param properties 同步配置
     * @return 解密后的 Map，未启用或不匹配时返回原对象
     * @throws IllegalArgumentException 已加密的消息解密失败
     */
    public static Object decrypt(Object message, SyncProperties properties) {
        if (!isEnabled(properties) || message == null || !(message instanceof Map<?, ?> map)) {
            return message;
        }
        if (!Boolean.TRUE.equals(map.get(ENCRYPTED))) {
            return message;
        }
        byte[] plainBytes;
        try {
            String encoded = String.valueOf(map.get(CIPHER));
            String[] segments = encoded.split(":");
            if (segments.length == V2_SEGMENTS) {
                plainBytes = decryptV2(properties, segments);
            } else if (segments.length == V1_SEGMENTS) {
                plainBytes = decryptV1(properties, segments);
            } else {
                throw new IllegalArgumentException("密文段数非法: " + segments.length);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("同步消息解密失败（密钥不一致或数据被篡改）: " + e.getMessage(), e);
        }
        try {
            return Json.fromJson(new String(plainBytes, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("同步消息解密后不是合法 JSON", e);
        } finally {
            Arrays.fill(plainBytes, (byte) 0);
        }
    }

    /**
     * 解密 v2 密文。
     *
     * @param properties 同步配置
     * @param segments   盐、IV、密文
     * @return 明文字节
     * @throws Exception 解密失败
     */
    private static byte[] decryptV2(SyncProperties properties, String[] segments) throws Exception {
        byte[] salt = Base64.getDecoder().decode(segments[0]);
        byte[] iv = Base64.getDecoder().decode(segments[1]);
        byte[] cipherBytes = Base64.getDecoder().decode(segments[2]);
        byte[] key = Hkdf.derive(properties.getCryptoSecret().getBytes(StandardCharsets.UTF_8),
                salt, HKDF_INFO, KEY_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, AES_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(AAD);
            return cipher.doFinal(cipherBytes);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * 解密旧版（v1）密文：固定密钥 + 随机 IV。
     *
     * @param properties 同步配置
     * @param segments   IV、密文
     * @return 明文字节
     * @throws Exception 解密失败
     */
    private static byte[] decryptV1(SyncProperties properties, String[] segments) throws Exception {
        byte[] iv = Base64.getDecoder().decode(segments[0]);
        byte[] cipherBytes = Base64.getDecoder().decode(segments[1]);
        byte[] key = java.security.MessageDigest.getInstance(SHA_256)
                .digest(properties.getCryptoSecret().getBytes(StandardCharsets.UTF_8));
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, AES_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherBytes);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }
}
