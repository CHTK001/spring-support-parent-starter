package com.chua.common.support.cache;

/**
 * 缓存提供者
 *
 * @author CH
 */
public interface CacheProvider {

    static CacheProvider auto() {
        throw new UnsupportedOperationException("No CacheProvider implementation found");
    }

    void put(String key, Object value);

    default Object get(String key, Class<?> type) {
        return null;
    }

    default void remove(String key) {
    }

    default void clear() {
    }
}
