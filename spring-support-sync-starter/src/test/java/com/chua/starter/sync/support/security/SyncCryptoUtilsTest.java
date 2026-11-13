package com.chua.starter.sync.support.security;

import com.chua.starter.sync.support.properties.SyncProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 同步消息加密：v2 信封、旧版兼容、篡改与失败不降级。
 *
 * <p>「同 payload 两次密文不同」是每消息随机的敏感性对照；「篡改抛异常」则是
 * 静默降级的敏感性对照——若把 catch 改回返回原文，这两个用例会失败。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
class SyncCryptoUtilsTest {

    /**
     * 测试密钥
     */
    private static final String SECRET = "sync-unit-test-secret";

    /**
     * v2 往返且每条消息密文不同。
     */
    @Test
    @DisplayName("v2 可还原且每消息密文不同")
    void v2RoundTrip() {
        SyncProperties properties = properties(2);
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "update");
        payload.put("id", 7);

        Map<?, ?> first = asMap(SyncCryptoUtils.encrypt(payload, properties));
        Map<?, ?> second = asMap(SyncCryptoUtils.encrypt(payload, properties));

        assertEquals(Boolean.TRUE, first.get(SyncCryptoUtils.ENCRYPTED));
        assertEquals(SyncCryptoUtils.ALGORITHM_V2, first.get(SyncCryptoUtils.ALGORITHM));
        assertNotEquals(first.get(SyncCryptoUtils.CIPHER), second.get(SyncCryptoUtils.CIPHER));

        Map<?, ?> decrypted = asMap(SyncCryptoUtils.decrypt(first, properties));
        assertEquals("update", decrypted.get("event"));
        assertEquals(7, ((Number) decrypted.get("id")).intValue());
    }

    /**
     * 版本 1 仍然产出旧版两段式信封，且能被解回。
     */
    @Test
    @DisplayName("版本 1 兼容旧信封")
    void versionOneKeepsLegacyEnvelope() {
        SyncProperties properties = properties(1);
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "legacy");

        Map<?, ?> envelope = asMap(SyncCryptoUtils.encrypt(payload, properties));
        assertEquals(SyncCryptoUtils.ALGORITHM_V1, envelope.get(SyncCryptoUtils.ALGORITHM));
        assertEquals(2, String.valueOf(envelope.get(SyncCryptoUtils.CIPHER)).split(":").length);

        Map<?, ?> decrypted = asMap(SyncCryptoUtils.decrypt(envelope, properties));
        assertEquals("legacy", decrypted.get("event"));
    }

    /**
     * 篡改密文必须抛异常，而不是把密文交给业务。
     */
    @Test
    @DisplayName("篡改密文即失败")
    void tamperedCipherRejected() {
        SyncProperties properties = properties(2);
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "update");
        Map<?, ?> envelope = asMap(SyncCryptoUtils.encrypt(payload, properties));

        Map<String, Object> tampered = new HashMap<>();
        envelope.forEach((key, value) -> tampered.put(String.valueOf(key), value));
        String cipher = String.valueOf(tampered.get(SyncCryptoUtils.CIPHER));
        char last = cipher.charAt(cipher.length() - 1);
        tampered.put(SyncCryptoUtils.CIPHER,
                cipher.substring(0, cipher.length() - 1) + (last == 'A' ? 'B' : 'A'));

        assertThrows(IllegalArgumentException.class, () -> SyncCryptoUtils.decrypt(tampered, properties));
    }

    /**
     * 密钥不一致时抛异常。
     */
    @Test
    @DisplayName("密钥不一致即失败")
    void wrongSecretRejected() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "update");
        Map<?, ?> envelope = asMap(SyncCryptoUtils.encrypt(payload, properties(2)));

        SyncProperties other = new SyncProperties();
        other.setCryptoSecret(SECRET + "-other");
        assertThrows(IllegalArgumentException.class, () -> SyncCryptoUtils.decrypt(envelope, other));
    }

    /**
     * 未配置密钥时原样透传，不尝试加解密。
     */
    @Test
    @DisplayName("未配置密钥时原样透传")
    void disabledPassesThrough() {
        SyncProperties properties = new SyncProperties();
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "plain");

        assertSame(payload, SyncCryptoUtils.encrypt(payload, properties));
        assertSame(payload, SyncCryptoUtils.decrypt(payload, properties));
    }

    /**
     * 构造测试配置。
     *
     * @param version 加密版本
     * @return 配置
     */
    private static SyncProperties properties(int version) {
        SyncProperties properties = new SyncProperties();
        properties.setCryptoSecret(SECRET);
        properties.setCryptoVersion(version);
        return properties;
    }

    /**
     * 断言并转换为 Map。
     *
     * @param value 值
     * @return Map
     */
    private static Map<?, ?> asMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<?, ?>) value;
    }
}
