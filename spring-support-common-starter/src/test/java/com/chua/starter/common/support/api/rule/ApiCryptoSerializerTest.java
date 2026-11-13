package com.chua.starter.common.support.api.rule;

import com.chua.common.support.lang.algorithm.cipher.Sm2Cipher;
import com.chua.common.support.lang.algorithm.crypto.Codec;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.starter.common.support.api.annotations.ApiFieldCrypto;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段级加密：按类型分派到真实算法、SM2 可被私钥还原、缺密钥即失败。
 *
 * <p>三条敏感性对照：密文不等于明文 hex（挡住退回 hex）、SM2 能用配对的私钥解回
 * （挡住「一次性密钥对」那种不可恢复写法）、空密钥抛异常（挡住静默明文）。</p>
 *
 * <p>算法实现本身在 {@code utils-support-common-starter} 的 {@code Codec} SPI 里，
 * 本类只验证 spring 侧的分派与失败策略。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
class ApiCryptoSerializerTest {

    /**
     * 测试密钥
     */
    private static final String KEY = "field-crypto-key";

    /**
     * 测试明文
     */
    private static final String PLAIN = "13800000000";

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * AES 类型走 AES，而不是 hex。
     */
    @Test
    @DisplayName("AES 走真实加密")
    void aesUsesRealCipher() throws Exception {
        String cipher = serializeValue(ApiFieldCrypto.ApiCryptoType.AES, KEY, PLAIN);
        assertNotEquals(Hex.encodeHexString(PLAIN.getBytes(StandardCharsets.UTF_8)), cipher);
        assertEquals(PLAIN, Codec.build("aes", KEY).decodeHex(cipher));
    }

    /**
     * SM4 类型走 SM4，而不是 hex。
     */
    @Test
    @DisplayName("SM4 走真实加密")
    void sm4UsesRealCipher() throws Exception {
        String cipher = serializeValue(ApiFieldCrypto.ApiCryptoType.SM4, KEY, PLAIN);
        assertNotEquals(Hex.encodeHexString(PLAIN.getBytes(StandardCharsets.UTF_8)), cipher);
        assertEquals(PLAIN, Codec.build("sm4", KEY).decodeHex(cipher));
    }

    /**
     * SM2 用注解里的公钥加密，配对的私钥必须能解回来。
     */
    @Test
    @DisplayName("SM2 可用配对私钥还原")
    void sm2IsRecoverable() throws Exception {
        Sm2Cipher sm2 = Sm2Cipher.create("bc");
        KeyPair keyPair = sm2.generateKeyPair();
        String publicKeyHex = Hex.encodeHexString(keyPair.getPublic().getEncoded());

        Map<?, ?> result = MAPPER.readValue(
                serialize(ApiFieldCrypto.ApiCryptoType.SM2, publicKeyHex, PLAIN), Map.class);
        String cipher = String.valueOf(result.get("value"));
        assertEquals(publicKeyHex, result.get("valueKeyId"));

        byte[] plain = sm2.decrypt(keyPair.getPrivate().getEncoded(), Hex.decodeHex(cipher));
        assertEquals(PLAIN, new String(plain, StandardCharsets.UTF_8));
    }

    /**
     * 缺少密钥必须抛异常，不能输出明文。
     */
    @Test
    @DisplayName("缺密钥即失败")
    void missingKeyRejected() {
        assertThrows(IllegalStateException.class,
                () -> serializeValue(ApiFieldCrypto.ApiCryptoType.AES, "", PLAIN));
    }

    /**
     * 空值写 null。
     */
    @Test
    @DisplayName("空值写 null")
    void nullWritesNull() throws Exception {
        Map<?, ?> result = MAPPER.readValue(serialize(null, KEY, null), Map.class);
        assertTrue(result.containsKey("value"));
        assertNull(result.get("value"));
    }

    /**
     * 只取序列化后的 value 字段。
     *
     * @param type  加密类型
     * @param key   密钥
     * @param plain 明文
     * @return 密文
     * @throws Exception 序列化失败
     */
    private static String serializeValue(ApiFieldCrypto.ApiCryptoType type, String key, String plain)
            throws Exception {
        return String.valueOf(MAPPER.readValue(serialize(type, key, plain), Map.class).get("value"));
    }

    /**
     * 按字段序列化器输出一段 JSON。
     *
     * @param type  加密类型
     * @param key   密钥
     * @param plain 明文
     * @return JSON
     * @throws Exception 序列化失败
     */
    private static String serialize(ApiFieldCrypto.ApiCryptoType type, String key, String plain)
            throws Exception {
        StringWriter writer = new StringWriter();
        JsonGenerator generator = new JsonFactory().createGenerator(writer);
        generator.writeStartObject();
        generator.writeFieldName("value");
        new ApiCryptoSerializer(type, key).serialize(plain, generator, null);
        generator.writeEndObject();
        generator.close();
        return writer.toString();
    }
}
