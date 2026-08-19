package com.chua.starter.common.support.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变 Map 构造器。
 */
public final class ImmutableBuilder {

    private ImmutableBuilder() {
    }

    public static ImmutableStringStringMapBuilder builderOfStringStringMap() {
        return new ImmutableStringStringMapBuilder();
    }

    public static final class ImmutableStringStringMapBuilder {

        private final Map<String, String> map = new LinkedHashMap<>();

        public ImmutableStringStringMapBuilder put(String key, String value) {
            map.put(key, value);
            return this;
        }

        public Map<String, String> asSynchronizedMap() {
            return Collections.synchronizedMap(Collections.unmodifiableMap(new LinkedHashMap<>(map)));
        }
    }
}

