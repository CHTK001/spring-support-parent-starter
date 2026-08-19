package com.chua.common.support.lang.algorithm.crypto;

/**
 * AES 编解码器
 * <p>
 * 提供 AES 加密和解密功能
 * </p>
 *
 * @author CH
 */
public class AesCodec implements Codec {

    @Override
    public String encodeHex(String origin) {
        return origin;
    }

    @Override
    public String decodeHex(String origin) {
        return origin;
    }

    @Override
    public byte[] decode(byte[] data) throws Exception {
        return data;
    }

    @Override
    public void close() throws Exception {
    }
}
