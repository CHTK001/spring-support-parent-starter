// [P3C A20 豁免] A20/A30/A31: 含算法名/提供者名等协议常量，属非业务魔法值场景。
package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.lang.algorithm.cipher.Sm4Cipher;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.spi.annotations.Spi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * SM4 编解码实现（真实加密，不再是十六进制伪装）。
 *
 * <p>实现由 SPI 提供者 {@code bc}（BouncyCastle）承担；任意长度口令经
 * {@code SHA-256} 取前 16 字节作为 SM4 密钥，密文以十六进制输出。</p> *
 * <p>历史版本对 {@code sm4} 类型只做了 hex 编码，旧数据无法当作密文解开；
 * 这里不做兼容回退，避免把明文当密文继续传给业务。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Spi({"sm4", "sm4-ecb"})
public class Sm4Codec implements Codec {

    /**
     * SPI 提供者名
     */
    private static final String PROVIDER = "bc";

    /**
     * 摘要算法名
     */
    private static final String SHA_256 = "SHA-256";

    /**
     * SM4 密钥长度（字节）
     */
    private static final int KEY_LENGTH = 16;

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
        byte[] cipherText = Sm4Cipher.create(PROVIDER)
                .encrypt(deriveKey(), data.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(cipherText);
    }

    @Override
    public String decodeHex(String hex) {
        if (hex == null) {
            return null;
        }
        byte[] plain = Sm4Cipher.create(PROVIDER).decrypt(deriveKey(), Hex.decodeHex(hex));
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 由口令派生 16 字节 SM4 密钥。
     *
     * @return 密钥
     */
    private byte[] deriveKey() {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("SM4 密钥不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256)
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, KEY_LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 密钥派生失败", e);
        }
    }
}
