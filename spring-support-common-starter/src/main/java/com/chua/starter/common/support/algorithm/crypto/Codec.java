package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.starter.common.support.crypto.AesCodec;

import java.nio.charset.StandardCharsets;

/**
 * 编解码器。
 *
 * @author CH
 * @since 4.0.0
 */
public class Codec {

    /**
     * 加密类型
     */
    private final String type;

    /**
     * 密钥
     */
    private final String key;

    /**
     * 构建编解码器。
     *
     * @param type 加密类型，aes 表示 AES 加解密，其他为 Hex 编解码
     * @param key  密钥
     * @return 编解码器
     */
    public static Codec build(String type, String key) {
        /**
         * Codec。
         * @param type 类型
         * @param key 钥匙
         */
        return new Codec(type, key);
    }

    /**
     * 构造器。
     *
     * @param type 加密类型
     * @param key  密钥
     */
    private Codec(String type, String key) {
        this.type = type;
        this.key = key;
    }

    /**
     * 编码为十六进制字符串。
     *
     * @param data 原始数据
     * @return 编码结果
     */
    public String encodeHex(String data) {
        if (data == null) {
            return null;
        }
        if (isAes()) {
            /**
              * aescodec。
             * @param key.getBytes(StandardCharsets.UTF_8 键.获取bytes(标准字符集.UTF_8
             * @return AesCodec的结果
             */
            return new AesCodec(key.getBytes(StandardCharsets.UTF_8)).encodeHex(data);
        }
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return Hex.encodeHexString(bytes);
    }

    /**
     * 解码十六进制字符串。
     *
     * @param hex 编码数据
     * @return 解码结果
     */
    public String decodeHex(String hex) {
        if (hex == null) {
            return null;
        }
        if (isAes()) {
            /**
              * aescodec。
             * @param key.getBytes(StandardCharsets.UTF_8 键.获取bytes(标准字符集.UTF_8
             * @return AesCodec的结果
             */
            return new AesCodec(key.getBytes(StandardCharsets.UTF_8)).decodeHex(hex);
        }
        byte[] bytes = Hex.decodeHex(hex);
        /**
         * 字符串。
         * @param bytes bytes
         * @param StandardCharsets.UTF_8 标准字符集.utf8
         * @return 字符串的结果
         */
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 是否为 AES 加解密。
     *
     * @return 是否使用 AES
     */
    private boolean isAes() {
        return type != null && type.toLowerCase().contains("aes");
    }
}
