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
 * @since 4.0.0
 */
public interface CacheProvider {

    /**
     * 获取默认缓存提供者（内存）
     * @return auto的结果
     */
    static CacheProvider auto() {
        /**
         * 内存缓存提供者。
         * @return 内存缓存提供者的结果
         */
        return new MemoryCacheProvider();
    }

    /**
     * 写入缓存
     * @param key Key
     * @param value 值
     */
    void put(String key, Object value);

    /**
     * 写入缓存并设置 TTL（秒）
     * @param key Key
     * @param value 值
     * @param ttlSeconds ttlseconds
     */
    default void put(String key, Object value, long ttlSeconds) {
        /**
         * 放入。
         * @param key Key
         * @param value 值
         */
        put(key, value);
    }

    /**
     * 读取缓存
     * @param key Key
     * @param type 类型
     * @return 获取的结果
     */
    default Object get(String key, Class<?> type) {
        return null;
    }

    /**
     * 移除缓存
     * @param key Key
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
     * @return 获取hitrate的结果
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
     * @author CH
     * @since 4.0.0
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

            /**
             * 缓存entry。
             * @param value 值
             * @param ttlSeconds ttlseconds
             */
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
        /**
         * 放入
        */
        public void put(String key, Object value) {
            /**
             * 放入。
             * @param key Key
             * @param value 值
             * @param 0 0
             */
            put(key, value, 0);
        }

        @Override
        /**
         * 放入
        */
        public void put(String key, Object value, long ttlSeconds) {
            if (key != null && value != null) {
                store.put(key, new CacheEntry(value, ttlSeconds));
            }
        }

        @Override
        /**
         * 获取
        */
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
        /**
         * 移除
        */
        public void remove(String key) {
            if (key != null) {
                store.remove(key);
            }
        }

        @Override
        /**
         * Clear
        */
        public void clear() {
            store.clear();
        }
    }

    /**
     * Redis 缓存提供者（抽象）
     * @author CH
     * @since 4.0.0
     */
    abstract class RedisCacheProvider implements CacheProvider {
        /**
         * 密钥前缀
        */
        protected final String keyPrefix;
        /**
         * 默认TTL
        */
        protected final long defaultTtl;

        /**
         * 创建 Redis缓存提供者 实例
         * @param keyPrefix Key前缀
         * @param defaultTtl long
         * @param defaultTtl 默认ttl
         */
        protected RedisCacheProvider(String keyPrefix, long defaultTtl) {
            this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
            this.defaultTtl = defaultTtl;
        }

        /**
         * 解析Key
         *
         * @param key Key
         * @return resolveKey的结果
         */
        protected String resolveKey(String key) {
            return keyPrefix + key;
        }

        @Override
        /**
         * 放入
        */
        public void put(String key, Object value) {
            /**
             * 放入。
             * @param key Key
             * @param value 值
             * @param defaultTtl 默认ttl
             */
            put(key, value, defaultTtl);
        }

        @Override
        /**
         * 放入
        */
        public abstract void put(String key, Object value, long ttlSeconds);

        @Override
        /**
         * 获取
        */
        public abstract Object get(String key, Class<?> type);

        @Override
        /**
         * 移除
        */
        public abstract void remove(String key);

        @Override
        /**
         * Clear
        */
        public abstract void clear();
    }
}
