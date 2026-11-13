// [P3C A20 豁免] A20/A30/A31: 含算法名等协议常量，属非业务魔法值场景。
package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.lang.algorithm.cipher.DesCipher;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.spi.annotations.Spi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * DES 编解码实现。
 *
 * <p>DES 只用于兼容老配置；新项目请用 AES 或 SM4。任意长度口令经
 * {@code SHA-256} 取前 8 字节作为 DES 密钥（DES 的有效密钥长度只有 56 位）。</p>
 *
 * @author CH
 * @since 4.0.0
 * @deprecated DES 密钥空间过小，请改用 AES 或 SM4
 */
@Deprecated(since = "4.0.0.51", forRemoval = false)
@Spi({"des"})
public class DesCodec implements Codec {

    /**
     * 摘要算法名
     */
    private static final String SHA_256 = "SHA-256";

    /**
     * DES 密钥长度（字节）
     */
    private static final int KEY_LENGTH = 8;

    /**
     * 密钥原文
     */
    private String key;

    @Override
    public Codec key(String key) {
        this.key = key;
        return this;
    }

    @Override
    public String encodeHex(String data) {
        if (data == null) {
            return null;
        }
        byte[] cipherText = DesCipher.create("jdk")
                .encrypt(deriveKey(), data.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(cipherText);
    }

    @Override
    public String decodeHex(String hex) {
        if (hex == null) {
            return null;
        }
        byte[] plain = DesCipher.create("jdk").decrypt(deriveKey(), Hex.decodeHex(hex));
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 由口令派生 8 字节 DES 密钥。
     *
     * @return 密钥
     */
    private byte[] deriveKey() {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("DES 密钥不能为空");
        }
        try {
            return Arrays.copyOf(MessageDigest.getInstance(SHA_256)
                    .digest(key.getBytes(StandardCharsets.UTF_8)), KEY_LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("DES 密钥派生失败", e);
        }
    }
}
