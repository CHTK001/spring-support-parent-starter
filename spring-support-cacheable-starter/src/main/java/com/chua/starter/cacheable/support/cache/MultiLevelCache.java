package com.chua.starter.cacheable.support.cache;

import com.chua.common.support.value.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * cache
 *
 * @author CH
 * @since 2022/8/10 13:03
 */
@Slf4j
public class MultiLevelCache extends AbstractValueAdaptingCache {
    private final String name;
    private final Map<String, ReentrantLock> keyLockMap = new ConcurrentHashMap<>();
    private final MultiLevelCacheFactory multiLevelCacheFactory;
    private ApplicationContext applicationContext;


    public MultiLevelCache(String name, MultiLevelCacheFactory multiLevelCacheFactory) {
        super(multiLevelCacheFactory.isCacheNullValues());
        this.name = name;
        this.multiLevelCacheFactory = multiLevelCacheFactory;
    }

    @Override
    protected Object lookup(Object key) {
        String keyValue = getKey(key);
        Value<Object> value = multiLevelCacheFactory.getValue(name, keyValue);
        return value.getValue();
    }


    private String getKey(Object key) {
        return this.name.concat(":").concat(multiLevelCacheFactory.cachePrefix(key.toString()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value != null) {
            return (T) value;
        }

        ReentrantLock lock = keyLockMap.get(key.toString());
        if (lock == null) {
            if (log.isDebugEnabled()) {
                log.debug("create lock for key : {}", key);
            }
            lock = new ReentrantLock();
            keyLockMap.putIfAbsent(key.toString(), lock);
        }
        try {
            lock.lock();
            value = lookup(key);
            if (value != null) {
                return (T) value;
            }
            value = valueLoader.call();
            Object storeValue = toStoreValue(value);
            put(key, storeValue);
            return (T) value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (!super.isAllowNullValues() && value == null) {
            this.evict(key);
            return;
        }
        String keyValue = getKey(key);
        multiLevelCacheFactory.setValue(name, keyValue, value);
    }

    @Override
    public void evict(Object key) {
        multiLevelCacheFactory.evict(name, getKey(key));
    }

    @Override
    public void clear() {
        multiLevelCacheFactory.clear(name);
    }
}
