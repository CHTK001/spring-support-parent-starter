package com.chua.starter.common.support.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;

/**
 * 多级缓存实现
 * <p>
 * 支持多级缓存查询与回填。从 L1 开始查找，未命中则向更高层级查找，
 * 命中后自动回填到更低层级的缓存中，提升下一次访问的命中率。
 * </p>
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>多层级读写：L1 命中直接返回；L1 未命中则查询 L2 并回填到 L1。</li>
 *   <li>空值保护：可选地缓存空值占位符，防止缓存穿透。</li>
 *   <li>命中统计：内置命中/未命中计数器与命中率计算。</li>
 *   <li>异常隔离：单层失败不影响其他层级。</li>
 * </ul>
 *
 * @since 2024/7/22
 * @author CH
 */
public class MultiLevelCache implements Cache {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MultiLevelCache.class);

    /**
    * 空值占位符（用于防缓存穿透）
    */
    static final Object NULL_VALUE_PLACEHOLDER = new Object() {
        @Override
        /** 转为字符串 */
        public String toString() {
            return "NULL_VALUE";
        }
    };

    /** 名称 */
    private final String name;
    /** 缓存 */
    private final List<Cache> caches;

    /**
        * 缓存统计
        */
    private final LongAdder hitCount = new LongAdder();
    /** Miss数量 */
    private final LongAdder missCount = new LongAdder();
    /** Backfill数量 */
    private final LongAdder backfillCount = new LongAdder();

    /**
     * 是否缓存空值
     */
    private final boolean cacheNullValues;

    /**
     * 创建 multi水平仪缓存 实例
     * @param name 名称
     * @param caches 列表
     * @param caches 缓存
     */
    public MultiLevelCache(String name, List<Cache> caches) {
        /**
         * this。
         * @param name 名称
         * @param caches 缓存
         * @param false false
         */
        this(name, caches, false);
    }

    /**
     * 创建 multi水平仪缓存 实例
     * @param name 名称
     * @param caches 列表
     * @param caches 缓存
     * @param cacheNullValues 布尔值
     * @param cacheNullValues 缓存空值
     */
    public MultiLevelCache(String name, List<Cache> caches, boolean cacheNullValues) {
        this.name = name;
        this.caches = Objects.requireNonNull(caches, "缓存层级不能为空");
        this.cacheNullValues = cacheNullValues;
    }

    @Override
    /** 获取名称 */
    public String getName() {
        return name;
    }

    @Override
    /** 获取NAT缓存 */
    public Object getNativeCache() {
        return caches;
    }

    @Override
    /** 获取 */
    public ValueWrapper get(Object key) {
        if (key == null) {
            missCount.increment();
            return null;
        }
        return getInternal(key, null);
    }

    /**
    * 获取内部
    *
    * @param key Key
    * @param typeIndexHint 类型索引hint
    * @return 获取内部的结果
    */
    private ValueWrapper getInternal(Object key, Integer typeIndexHint) {
        for (int i = 0; i < caches.size(); i++) {
            Cache cache = caches.get(i);
            ValueWrapper wrapper;
            try {
                wrapper = cache.get(key);
            } catch (Exception e) {
                log.warn("缓存读取失败: cache={}, level={}, key={}", name, i, key, e);
                continue;
            }

            if (wrapper == null) {
                continue;
            }

            Object value = wrapper.get();
            // 空值占位符
            if (value == NULL_VALUE_PLACEHOLDER) {
                hitCount.increment();
                log.debug("缓存命中空值: cache={}, level={}, key={}", name, i, key);
                return NullValueWrapper.INSTANCE;
            }

            hitCount.increment();
            log.debug("缓存命中: cache={}, level={}, key={}", name, i, key);
            // 回填到更低层级
            /**
             * backfill。
             * @param key Key
             * @param value 值
             * @param 1 1
             */
            backfill(key, value, i - 1);
            return wrapper;
        }

        missCount.increment();
        log.debug("缓存未命中: cache={}, key={}", name, key);
        return null;
    }

    /**
     * 获取内部类型
     *
     * @param key Key
     * @param type 类型
     * @return 获取内部类型的结果
     */
    private <T> T getInternalTyped(Object key, Class<T> type) {
        if (key == null || type == null) {
            missCount.increment();
            return null;
        }

        for (int i = 0; i < caches.size(); i++) {
            Cache cache = caches.get(i);
            T value;
            try {
                value = cache.get(key, type);
            } catch (Exception e) {
                log.warn("缓存读取失败: cache={}, level={}, key={}", name, i, key, e);
                continue;
            }

            if (value == null) {
                continue;
            }

            hitCount.increment();
            log.debug("缓存命中: cache={}, level={}, key={}", name, i, key);
            /**
             * backfill。
             * @param key Key
             * @param value 值
             * @param 1 1
             */
            backfill(key, value, i - 1);
            return value;
        }

        missCount.increment();
        log.debug("缓存未命中: cache={}, key={}", name, key);
        return null;
    }

    @Override
    /** 获取 */
    public <T> T get(Object key, Class<T> type) {
        return getInternalTyped(key, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
    * 获取
    *
    * @param key Key
    * @param valueLoader 值加载
    * @return 获取的结果
    */
    public <T> T get(Object key, Callable<T> valueLoader) {
        if (key == null) {
            missCount.increment();
            return null;
        }

 // 先按普通 Key 查找各级缓存
        for (int i = 0; i < caches.size(); i++) {
            Cache cache = caches.get(i);
            try {
                ValueWrapper wrapper = cache.get(key);
                if (wrapper != null) {
                    Object cached = wrapper.get();
                    if (cached == NULL_VALUE_PLACEHOLDER) {
                        hitCount.increment();
                        return null;
                    }
                    hitCount.increment();
                    T value = (T) cached;
                    /**
                     * backfill。
                     * @param key Key
                     * @param value 值
                     * @param 1 1
                     */
                    backfill(key, value, i - 1);
                    log.debug("缓存命中: cache={}, level={}, key={}", name, i, key);
                    return value;
                }
            } catch (Exception e) {
                log.warn("缓存读取失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }

 // 所有层级均未命中，调用 值加载 加载并写入所有层级
        missCount.increment();
        T value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new Cache.ValueRetrievalException(key, valueLoader, e);
        }

        if (value != null) {
            /**
             * 放入。
             * @param key Key
             * @param value 值
             */
            put(key, value);
        } else if (cacheNullValues) {
            // 防缓存穿透：写入空值占位符
            /**
             * 放入空。
             * @param key Key
             */
            putNull(key);
        }
        return value;
    }

    /**
     * 回填缓存到指定层级及以下
     * @param key Key
     * @param value 值
     * @param toIndex 转为索引
     */
    private void backfill(Object key, Object value, int toIndex) {
        if (value == null || toIndex < 0) {
            return;
        }

        for (int i = toIndex; i >= 0; i--) {
            try {
                caches.get(i).put(key, value);
                backfillCount.increment();
                log.debug("缓存回填: cache={}, level={}, key={}", name, i, key);
            } catch (Exception e) {
                log.warn("缓存回填失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    @Override
    /** 放入 */
    public void put(Object key, Object value) {
        if (key == null) {
            return;
        }
        Object storeValue = value == null && cacheNullValues ? NULL_VALUE_PLACEHOLDER : value;

        for (int i = 0; i < caches.size(); i++) {
            try {
                if (storeValue == null) {
                    // 不缓存空值
                    continue;
                }
                caches.get(i).put(key, storeValue);
            } catch (Exception e) {
                log.warn("缓存写入失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    /**
     * 写入空值占位符（防缓存穿透）。
     *
     * @param key 缓存键
     */
    public void putNull(Object key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < caches.size(); i++) {
            try {
                caches.get(i).put(key, NULL_VALUE_PLACEHOLDER);
            } catch (Exception e) {
                log.warn("缓存空值写入失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    @Override
    /** Evict */
    public void evict(Object key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < caches.size(); i++) {
            try {
                caches.get(i).evict(key);
            } catch (Exception e) {
                log.warn("缓存清除失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    @Override
    /** Clear */
    public void clear() {
        for (int i = 0; i < caches.size(); i++) {
            try {
                caches.get(i).clear();
            } catch (Exception e) {
                log.warn("缓存清空失败: cache={}, level={}", name, i, e);
            }
        }
    }

    /**
     * 按模式清除缓存（尽力而为，不保证完全匹配）。
     *
     * @param pattern 模式字符串（不区分大小写 contains 匹配）
     * @return 受影响的缓存层级数量
     */
    public int evictByPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        String lowerPattern = pattern.toLowerCase();
        int affected = 0;
        for (int i = 0; i < caches.size(); i++) {
            try {
                Cache cache = caches.get(i);
 // 仅当底层 缓存 支持按模式清除时使用，否则调用 clear
                if (cache instanceof PatternEvictable patternEvictable) {
                    patternEvictable.evictByPattern(lowerPattern);
                } else {
                    cache.clear();
                }
                affected++;
            } catch (Exception e) {
                log.warn("按模式清除缓存失败: cache={}, level={}, pattern={}", name, i, pattern, e);
            }
        }
        return affected;
    }

    /**
     * 获取命中次数
     *
     * @return 命中次数
     */
    public long getHitCount() {
        return hitCount.sum();
    }

    /**
     * 获取未命中次数
     *
     * @return 未命中次数
     */
    public long getMissCount() {
        return missCount.sum();
    }

    /**
     * 获取命中率
     *
     * @return 命中率（0-1）
     */
    public double getHitRate() {
        long hits = hitCount.sum();
        long total = hits + missCount.sum();
        return total == 0 ? 0.0d : (double) hits / total;
    }

    /**
     * 获取回填次数
     *
     * @return 回填次数
     */
    public long getBackfillCount() {
        return backfillCount.sum();
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        hitCount.reset();
        missCount.reset();
        backfillCount.reset();
    }

    /**
     * 获取缓存层级数
     *
     * @return 缓存层级数
     */
    public int getLevelCount() {
        return caches.size();
    }

    /**
     * 获取只读快照。
     *
     * @return 缓存层级快照
     */
    public List<Cache> getCaches() {
        return List.copyOf(caches);
    }

    /**
     * 空值包装器
     * @author CH
     * @since 4.0.0
     */
    private enum NullValueWrapper implements ValueWrapper {
        /**
         * 单例空值包装器
         */
        INSTANCE;

        @Override
        /** 获取 */
        public Object get() {
            return null;
        }
    }

    /**
        * 支持按模式清除的缓存接口
        * @author CH
        * @since 4.0.0
        */
    public interface PatternEvictable {

        /**
         * 按模式清除缓存
         *
         * @param pattern 模式字符串（小写）
         */
        void evictByPattern(String pattern);
    }
}
