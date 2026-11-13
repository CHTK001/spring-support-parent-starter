package com.chua.starter.common.support.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AES 编解码器：新格式的认证性、随机性与旧数据的兼容读取。
 *
 * <p>其中「同明文两次加密结果不同」是固定 IV 的敏感性对照：把 IV 改回
 * 由密钥摘要派生，该用例立刻失败。旧格式用例则用旧算法现场生成密文，
 * 保证迁移后历史台账与凭据仍然读得出来。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
class AesCodecTest {

    /**
     * 测试密钥
     */
    private static final String KEY = "unit-test-key-0123456789";

    /**
     * 新格式必须可逆，且带版本前缀。
     */
    @Test
    @DisplayName("新格式可逆且带版本前缀")
    void newFormatRoundTrip() {
        AesCodec codec = new AesCodec(KEY.getBytes(StandardCharsets.UTF_8));
        String cipher = codec.encodeHex("月英的密文");
        assertTrue(cipher.startsWith("g1:"), "新密文应带 g1: 前缀");
        assertEquals("月英的密文", codec.decodeHex(cipher));
    }

    /**
     * 同明文两次加密必须不同，否则 IV 又被写死了。
     */
    @Test
    @DisplayName("同明文两次加密结果不同")
    void newFormatUsesRandomIv() {
        AesCodec codec = new AesCodec(KEY.getBytes(StandardCharsets.UTF_8));
        assertNotEquals(codec.encodeHex("same"), codec.encodeHex("same"));
    }

    /**
     * 篡改密文必须认证失败，而不是解出半截明文。
     */
    @Test
    @DisplayName("篡改密文即失败")
    void tamperedCipherRejected() {
        AesCodec codec = new AesCodec(KEY.getBytes(StandardCharsets.UTF_8));
        String cipher = codec.encodeHex("secret");
        char last = cipher.charAt(cipher.length() - 1);
        char flipped = last == '0' ? '1' : '0';
        String tampered = cipher.substring(0, cipher.length() - 1) + flipped;
        assertThrows(RuntimeException.class, () -> codec.decodeHex(tampered));
    }

    /**
     * 旧 CBC 密文（无前缀、固定 IV）必须仍可解密。
     */
    @Test
    @DisplayName("旧 CBC 密文仍可读")
    void legacyCipherStillReadable() throws Exception {
        String plain = "历史台账口令";
        String legacy = legacyEncrypt(plain);
        AesCodec codec = new AesCodec(KEY.getBytes(StandardCharsets.UTF_8));
        assertEquals(plain, codec.decodeHex(legacy));
    }

    /**
     * 空值原样返回。
     */
    @Test
    @DisplayName("空值原样返回")
    void nullPassesThrough() {
        AesCodec codec = new AesCodec(KEY.getBytes(StandardCharsets.UTF_8));
        assertNull(codec.encodeHex(null));
        assertNull(codec.decodeHex(null));
    }

    /**
     * 非偶数长度十六进制应当失败，而不是静默截断。
     */
    @Test
    @DisplayName("非法十六进制即失败")
    void invalidHexRejected() {
        AesCodec codec = new AesCodec(KEY.getBytes(StandardCharsets.UTF_8));
        assertThrows(RuntimeException.class, () -> codec.decodeHex("abc"));
    }

    /**
     * 按旧实现生成 CBC 密文：IV 取密钥摘要前 16 字节，密文不含 IV。
     *
     * @param plain 明文
     * @return 十六进制密文
     * @throws Exception 加密失败
     */
    private String legacyEncrypt(String plain) throws Exception {
        byte[] keyBytes = KEY.getBytes(StandardCharsets.UTF_8);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(keyBytes);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(hash, "AES"),
                // [P3C A20 豁免]
                new IvParameterSpec(Arrays.copyOf(hash, 16)));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(encrypted.length * 2);
        for (byte b : encrypted) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
