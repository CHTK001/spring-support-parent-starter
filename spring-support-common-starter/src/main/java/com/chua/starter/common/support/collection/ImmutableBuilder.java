package com.chua.starter.common.support.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变 Map 构造器。
 */
public final class ImmutableBuilder {

    /** 创建 ImmutableBuilder 实例 */
    private ImmutableBuilder() {
    }

    /** BuilderOfStringStringMap */
    public static ImmutableStringStringMapBuilder builderOfStringStringMap() {
        return new ImmutableStringStringMapBuilder();
    }

    public static final class ImmutableStringStringMapBuilder {

        private final Map<String, String> map = new LinkedHashMap<>();

        /** Put */
        public ImmutableStringStringMapBuilder put(String key, String value) {
            map.put(key, value);
            return this;
        }

        /** AsSynchronizedMap */
        public Map<String, String> asSynchronizedMap() {
            return Collections.synchronizedMap(Collections.unmodifiableMap(new LinkedHashMap<>(map)));
        }
    }
}

