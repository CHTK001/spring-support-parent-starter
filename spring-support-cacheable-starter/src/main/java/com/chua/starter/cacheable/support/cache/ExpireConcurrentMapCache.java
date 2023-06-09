package com.chua.starter.cacheable.support.cache;

import com.chua.common.support.value.DelegateExpirationValue;
import com.chua.common.support.value.ExpirationValue;
import com.chua.common.support.value.Value;
import com.chua.starter.cacheable.support.manager.ConcurrentMapCache;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 过期
 *
 * @author CH
 */
public class ExpireConcurrentMapCache extends ConcurrentMapCache {
    private final long expire;

    public ExpireConcurrentMapCache(String name, long expire) {
        super(name);
        this.expire = expire;
    }

    @Override
    protected Object lookup(Object key) {
        Object lookup = super.lookup(key);
        if (lookup instanceof ExpirationValue && ((ExpirationValue) lookup).isExpiration()) {
            super.evict(key);
            return null;
        }
        return lookup;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return (T) ((Value) super.get(key, valueLoader)).getValue();
    }

    @Override
    public void put(Object key, Object value) {
        super.put(key, new DelegateExpirationValue<>(value, expire, TimeUnit.SECONDS));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        return super.putIfAbsent(key, new DelegateExpirationValue<>(value, expire, TimeUnit.SECONDS));
    }
}
