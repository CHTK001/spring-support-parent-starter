package com.chua.common.support.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存提供者
 * <p>
 * 提供统一的缓存抽象，支持设置 TTL。
 * </p>
 *
 * @author CH
 */
public interface CacheProvider {

    /**
     * 获取默认缓存提供者（内存）
     */
    static CacheProvider auto() {
        return new MemoryCacheProvider();
    }

    /**
     * 写入缓存
     */
    void put(String key, Object value);

    /**
     * 写入缓存并设置 TTL（秒）
     */
    default void put(String key, Object value, long ttlSeconds) {
        put(key, value);
    }

    /**
     * 读取缓存
     */
    default Object get(String key, Class<?> type) {
        return null;
    }

    /**
     * 移除缓存
     */
    default void remove(String key) {
    }

    /**
     * 清空缓存
     */
    default void clear() {
    }

    /**
     * 统计命中率
     */
    default double getHitRate() {
        return 0.0;
    }

    /**
     * 重置统计
     */
    default void resetStats() {
    }

    /**
     * 内存缓存提供者（支持 TTL 过期）
     */
    class MemoryCacheProvider implements CacheProvider {
        /**
         * 缓存条目
         */
        private static final class CacheEntry {
            /**
             * 缓存值
             */
            final Object value;
            /**
             * 过期时间戳（毫秒），0 表示永不过期
             */
            final long expireAt;

            CacheEntry(Object value, long ttlSeconds) {
                this.value = value;
                this.expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
            }

            /**
             * 是否已过期
             *
             * @return 是否已过期
             */
            boolean isExpired() {
                return expireAt > 0 && System.currentTimeMillis() > expireAt;
            }
        }

        /**
         * 存储容器
         */
        private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();

        @Override
        public void put(String key, Object value) {
            put(key, value, 0);
        }

        @Override
        public void put(String key, Object value, long ttlSeconds) {
            if (key != null && value != null) {
                store.put(key, new CacheEntry(value, ttlSeconds));
            }
        }

        @Override
        public Object get(String key, Class<?> type) {
            if (key == null) {
                return null;
            }
            CacheEntry entry = store.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.isExpired()) {
                store.remove(key);
                return null;
            }
            return entry.value;
        }

        @Override
        public void remove(String key) {
            if (key != null) {
                store.remove(key);
            }
        }

        @Override
        public void clear() {
            store.clear();
        }
    }

    /**
     * Redis 缓存提供者（抽象）
     */
    abstract class RedisCacheProvider implements CacheProvider {
        protected final String keyPrefix;
        protected final long defaultTtl;

        protected RedisCacheProvider(String keyPrefix, long defaultTtl) {
            this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
            this.defaultTtl = defaultTtl;
        }

        protected String resolveKey(String key) {
            return keyPrefix + key;
        }

        @Override
        public void put(String key, Object value) {
            put(key, value, defaultTtl);
        }

        @Override
        public abstract void put(String key, Object value, long ttlSeconds);

        @Override
        public abstract Object get(String key, Class<?> type);

        @Override
        public abstract void remove(String key);

        @Override
        public abstract void clear();
    }
}