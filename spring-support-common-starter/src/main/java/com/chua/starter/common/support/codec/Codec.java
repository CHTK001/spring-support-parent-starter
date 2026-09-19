package com.chua.starter.common.support.codec;

/**
 * Codec 兼容包装
 *
 * @author CH
 * @since 4.0.0
 */
public final class Codec {

    /** 创建 Codec 实例 */
    private Codec() {
    }

    /**
     * 构建
     *
     * @param type 类型
     * @param key Key
     * @return 构建的结果
     */
    public static com.chua.starter.common.support.algorithm.crypto.Codec build(String type, String key) {
        return com.chua.starter.common.support.algorithm.crypto.Codec.build(type, key);
    }
}
