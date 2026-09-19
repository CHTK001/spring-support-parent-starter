package com.chua.common.support.base.collection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 不可变构建器
 *
 * @author CH
 * @since 4.0.0
 */
public class ImmutableBuilder {

    /**
     * 内部持有的可变的字符串键值对映射
     * <p>容量随构建器链式调用逐步填充，无法预估，按需扩容。</p>
     */
    private final Map<String, String> map = new HashMap<>();

    /**
     * 创建并返回一个新的字符串键值对构建器实例
     * <p>用于以构建器模式逐步添加键值对，最终生成不可变集合视图。</p>
     *
     * @return ImmutableBuilder 实例
     */
    public static ImmutableBuilder builderOfStringStringMap() {
        /**
         * immutable构建器。
         */
        return new ImmutableBuilder();
    }

    /**
     * 向构建器中添加一个键值对
     *
     * @param key   键
     * @param value 值
     * @return 当前构建器实例，支持链式调用
     */
    public ImmutableBuilder put(String key, String value) {
        map.put(key, value);
        return this;
    }

    /**
     * 基于当前已添加的所有键值对，返回一个同步（线程安全）的 映射 视图
     * <p>返回的 Map 是对当前数据副本的包装，后续对构建器的修改不会影响已返回的 Map。</p>
     *
     * @return 线程安全的同步 映射
     */
    public Map<String, String> asSynchronizedMap() {
        return Collections.synchronizedMap(new HashMap<>(map));
    }
}
