package com.chua.starter.common.support.codec;

/**
 * Codec 兼容包装
 *
 */
public final class Codec {

    /** 创建 Codec 实例 */
    private Codec() {
    }

    /** 构建 */
    public static com.chua.starter.common.support.algorithm.crypto.Codec build(String type, String key) {
        return com.chua.starter.common.support.algorithm.crypto.Codec.build(type, key);
    }
}
