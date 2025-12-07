package com.chua.starter.common.support.cache;

import org.springframework.cache.Cache;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * 自定义缓存
 * @author CH
 * @since 2024/7/22
 */
public class CustomCache implements Cache {
    private final String name;
    private final List<Cache> caches;

    public CustomCache(String name, List<Cache> caches) {
        this.name = name;
        this.caches = caches;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return null;
    }

    @Override
    public ValueWrapper get(Object key) {
        int index = 0;
        return get(key, index);
    }

    private ValueWrapper get(Object key, int index) {
        // 检查索引是否超出缓存层级范围，如果是，则返回null。
        if (index >= caches.size()) {
            return null;
        }
        // 从指定层级的缓存中尝试获取值。
        Cache cache = caches.get(index);
        ValueWrapper t = cache.get(key);
        // 如果值不存在，则尝试在更高层级的缓存中获取，并递归尝试缓存。
        if(null == t) {
            return toUpperCached(get(key, index + 1), key, index - 1);
        }
        // 值存在时，同样进行递归缓存到更高层级。
        return toUpperCached(t, key, index - 1);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        int index = 0;
        return get(key, type, index);
    }

    private <T> T get(Object key, Class<T> type, int index) {
        // 检查索引是否超出缓存层级范围，如果是，则返回null。
        if (index >= caches.size()) {
            return null;
        }
        // 从指定层级的缓存中尝试获取值。
        Cache cache = caches.get(index);
        T t = cache.get(key, type);
        // 如果值不存在，则尝试在更高层级的缓存中获取，并递归尝试缓存。
        if(null == t) {
            return toUpperCached(get(key, type, index + 1), key, index - 1);
        }
        // 值存在时，同样进行递归缓存到更高层级。
        return toUpperCached(t, key, index - 1);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        int index = 0;
        return get(key, valueLoader, index);
    }
    /**
     * 从缓存中获取值，如果缓存中不存在，则通过调用Callable加载值，并可能将值缓存到多个层级的缓存中。
     *
     * @param key 要获取的缓存键。
     * @param valueLoader 如果缓存中不存在键，则用于加载值的Callable。
     * @param index 当前缓存层级的索引。
     * @param <T> 泛型参数，表示缓存的值类型。
     * @return 缓存的值，如果缓存中不存在且无法加载，则为null。
     */
    private <T> T get(Object key, Callable<T> valueLoader, int index) {
        // 检查索引是否超出缓存层级范围，如果是，则返回null。
        if (index >= caches.size()) {
            return null;
        }
        // 从指定层级的缓存中尝试获取值。
        Cache cache = caches.get(index);
        T t = cache.get(key, valueLoader);
        // 如果值不存在，则尝试在更高层级的缓存中获取，并递归尝试缓存。
        if(null == t) {
            return toUpperCached(get(key, valueLoader, index + 1), key, index - 1);
        }
        // 值存在时，同样进行递归缓存到更高层级。
        return toUpperCached(t, key, index - 1);
    }

    /**
     * 将值缓存到更高层级的缓存中。
     *
     * @param t 要缓存的值。
     * @param key 对应的缓存键。
     * @param index 当前缓存操作的层级索引。
     * @param <T> 泛型参数，表示缓存的值类型。
     * @return 缓存后的值。
     */
    private <T> T toUpperCached(T t, Object key, int index) {
        // 如果当前索引小于0，则不再进行缓存操作，直接返回值。
        Object value = t instanceof  ValueWrapper wrapper? wrapper.get() : t;
        if(null == value) {
            return null;
        }
        if (index < 0) {
            return t;
        }

        // 从当前层级开始，向上逐层缓存值�?
        for (int i = index; i > -1; i--) {
            caches.get(i).put(key, value);
        }
        // 返回缓存后的值。
        return t;
    }


    @Override
    public void put(Object key, Object value) {
        for (Cache cach : caches) {
            cach.put(key, value);
        }
    }

    @Override
    public void evict(Object key) {
        for (Cache cach : caches) {
            cach.evict(key);
        }
    }

    @Override
    public void clear() {
        for (Cache cach : caches) {
            cach.clear();
        }
    }
}

