package com.chua.starter.common.support.codec;

/**
 * Codec 兼容包装
 *
 */
public final class Codec {

    private Codec() {
    }

    public static com.chua.starter.common.support.algorithm.crypto.Codec build(String type, String key) {
        return com.chua.starter.common.support.algorithm.crypto.Codec.build(type, key);
    }
}
