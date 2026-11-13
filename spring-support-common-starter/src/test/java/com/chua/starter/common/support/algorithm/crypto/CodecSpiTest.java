package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.lang.algorithm.crypto.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加解密 SPI：类型解析、真实加密与失败不降级。
 *
 * <p>「密文不等于明文 hex」是假加密的敏感性对照：任一类型退化成 hex 编码，
 * 该用例都会失败；「未知类型抛异常」则挡住静默回退。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
class CodecSpiTest {

    /**
     * 测试密钥
     */
    private static final String KEY = "unit-test-codec-key";

    /**
     * 测试明文
     */
    private static final String PLAIN = "月英的字段";

    /**
     * AES 走真实加密且可还原。
     */
    @Test
    @DisplayName("AES 真实加密且可还原")
    void aesRoundTrip() {
        Codec codec = Codec.build("aes", KEY);
        String cipher = codec.encodeHex(PLAIN);
        assertNotEquals(Hex.encodeHexString(PLAIN.getBytes(StandardCharsets.UTF_8)), cipher);
        assertEquals(PLAIN, codec.decodeHex(cipher));
    }

    /**
     * SM4 走 SPI 的真实实现，密文可还原。
     */
    @Test
    @DisplayName("SM4 真实加密且可还原")
    void sm4RoundTrip() {
        Codec codec = Codec.build("sm4", KEY);
        String cipher = codec.encodeHex(PLAIN);
        assertNotEquals(Hex.encodeHexString(PLAIN.getBytes(StandardCharsets.UTF_8)), cipher);
        assertEquals(PLAIN, codec.decodeHex(cipher));
    }

    /**
     * 落库用的 AES-GCM 实现同样经 SPI 解析。
     */
    @Test
    @DisplayName("aes-gcm 走 SPI 且可还原")
    void aesGcmRoundTrip() {
        Codec codec = Codec.build("aes-gcm", KEY);
        String cipher = codec.encodeHex(PLAIN);
        assertTrue(cipher.startsWith("g1:"));
        assertEquals(PLAIN, codec.decodeHex(cipher));
    }

    /**
     * 未知类型必须失败，不能静默退化成 hex。
     */
    @Test
    @DisplayName("未知类型即失败")
    void unknownTypeRejected() {
        assertThrows(IllegalArgumentException.class, () -> Codec.build("rc4", KEY));
    }

    /**
     * 空密钥必须失败，不能拿空串当密钥加密。
     */
    @Test
    @DisplayName("空密钥即失败")
    void emptyKeyRejected() {
        Codec codec = Codec.build("aes", "");
        assertThrows(RuntimeException.class, () -> codec.encodeHex(PLAIN));
    }

    /**
     * hex 类型是显式编码，仅用于未配置密钥的兼容路径。
     */
    @Test
    @DisplayName("hex 类型是编码不是加密")
    void hexTypeIsExplicitEncoding() {
        Codec codec = Codec.build("hex", null);
        String encoded = codec.encodeHex(PLAIN);
        assertEquals(Hex.encodeHexString(PLAIN.getBytes(StandardCharsets.UTF_8)), encoded);
        assertEquals(PLAIN, codec.decodeHex(encoded));
    }
}
