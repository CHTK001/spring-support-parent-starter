package com.chua.starter.common.support.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变 映射 构造器。
 * @author CH
 * @since 4.0.0
 */
public final class ImmutableBuilder {

    /** 创建 immutable构建器 实例 */
    private ImmutableBuilder() {
    }

    /**
    * 构建器的字符串字符串映射
    *
    * @return 构建器的字符串字符串映射的结果
    */
    public static ImmutableStringStringMapBuilder builderOfStringStringMap() {
        /**
         * immutable字符串字符串映射构建器。
         * @return immutable字符串字符串映射构建器的结果
         */
        return new ImmutableStringStringMapBuilder();
    }

    public static final class ImmutableStringStringMapBuilder {

        private final Map<String, String> map = new LinkedHashMap<>();

        /**
         * 放入
         *
         * @param key Key
         * @param value 值
         * @return 放入的结果
         */
        public ImmutableStringStringMapBuilder put(String key, String value) {
            map.put(key, value);
            return this;
        }

        /**
         * as同步映射
         *
         * @return as同步映射的结果
         */
        public Map<String, String> asSynchronizedMap() {
            return Collections.synchronizedMap(Collections.unmodifiableMap(new LinkedHashMap<>(map)));
        }
    }
}

