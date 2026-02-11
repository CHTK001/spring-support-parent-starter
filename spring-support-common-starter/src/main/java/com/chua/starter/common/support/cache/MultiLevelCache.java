package com.chua.starter.common.support.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;

/**
 * 多级缓存实现
 * <p>
 * 支持多级缓存查询和回填，从L1开始查找，未命中则向更高层级查找，
 * 命中后自动回填到更低层级的缓存中。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/7/22
 */
@Slf4j
public class MultiLevelCache implements Cache {

    private final String name;
    private final List<Cache> caches;

    // 缓存统计
    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();

    public MultiLevelCache(String name, List<Cache> caches) {
        this.name = name;
        this.caches = caches;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return caches;
    }

    @Override
    public ValueWrapper get(Object key) {
        return get(key, 0);
    }

    private ValueWrapper get(Object key, int index) {
        if (index >= caches.size()) {
            missCount.increment();
            return null;
        }

        Cache cache = caches.get(index);
        ValueWrapper wrapper = cache.get(key);

        if (wrapper == null) {
            // 未命中，继续向上查找
            ValueWrapper found = get(key, index + 1);
            if (found != null) {
                // 回填到当前层级
                backfill(key, found.get(), index);
            }
            return found;
        }

        hitCount.increment();
        log.debug("缓存命中: cache={}, level={}, key={}", name, index, key);
        return wrapper;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return get(key, type, 0);
    }

    private <T> T get(Object key, Class<T> type, int index) {
        if (index >= caches.size()) {
            missCount.increment();
            return null;
        }

        Cache cache = caches.get(index);
        T value = cache.get(key, type);

        if (value == null) {
            T found = get(key, type, index + 1);
            if (found != null) {
                backfill(key, found, index);
            }
            return found;
        }

        hitCount.increment();
        log.debug("缓存命中: cache={}, level={}, key={}", name, index, key);
        return value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return get(key, valueLoader, 0);
    }

    private <T> T get(Object key, Callable<T> valueLoader, int index) {
        if (index >= caches.size()) {
            missCount.increment();
            return null;
        }

        Cache cache = caches.get(index);
        T value = cache.get(key, valueLoader);

        if (value == null) {
            T found = get(key, valueLoader, index + 1);
            if (found != null) {
                backfill(key, found, index);
            }
            return found;
        }

        hitCount.increment();
        return value;
    }

    /**
     * 回填缓存到指定层级及以下
     */
    private void backfill(Object key, Object value, int toIndex) {
        if (value == null || toIndex < 0) {
            return;
        }

        for (int i = toIndex; i >= 0; i--) {
            try {
                caches.get(i).put(key, value);
                log.debug("缓存回填: cache={}, level={}, key={}", name, i, key);
            } catch (Exception e) {
                log.warn("缓存回填失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        for (int i = 0; i < caches.size(); i++) {
            try {
                caches.get(i).put(key, value);
            } catch (Exception e) {
                log.warn("缓存写入失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    @Override
    public void evict(Object key) {
        for (int i = 0; i < caches.size(); i++) {
            try {
                caches.get(i).evict(key);
            } catch (Exception e) {
                log.warn("缓存清除失败: cache={}, level={}, key={}", name, i, key, e);
            }
        }
    }

    @Override
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
     * 获取命中次数
     */
    public long getHitCount() {
        return hitCount.sum();
    }

    /**
     * 获取未命中次数
     */
    public long getMissCount() {
        return missCount.sum();
    }

    /**
     * 获取命中率
     */
    public double getHitRate() {
        long hits = hitCount.sum();
        long total = hits + missCount.sum();
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        hitCount.reset();
        missCount.reset();
    }

    /**
     * 获取缓存层级数
     */
    public int getLevelCount() {
        return caches.size();
    }
}
