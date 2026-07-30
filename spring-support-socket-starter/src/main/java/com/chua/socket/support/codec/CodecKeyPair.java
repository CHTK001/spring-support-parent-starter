package com.chua.socket.support.codec;

/**
 * 编解码密钥对接口
 *
 * @author CH
 */
public interface CodecKeyPair {
    /**
     * 获取公钥（十六进制）
     */
    String getPublicKeyHex();

    /**
     * 获取私钥（十六进制）
     */
    String getPrivateKeyHex();

    /**
     * 编码数据
     *
     * @param data 待编码数据
     * @param key  密钥
     * @return 编码后的数据
     */
    String encode(String data, String key);

    /**
     * 解码数据
     *
     * @param data 待解码数据
     * @param key  密钥
     * @return 解码后的数据
     */
    String decode(String data, String key);
}
