package com.chua.starter.cacheable.support.handler;

import com.chua.starter.cacheable.support.manager.ConcurrentMapCache;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import com.chua.starter.cacheable.support.value.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.cache.Cache;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理器
 *
 * @author CH
 * @since 2022/8/10 13:55
 */
@Slf4j
public abstract class AbstractCacheManagerHandler implements CacheManagerHandler {
    protected ApplicationContext applicationContext;

    protected Map<String, Cache> cacheMap = new ConcurrentHashMap<>();
    protected CacheProperties.CachePoolProperties cachePoolProperties;
    private boolean cacheNullValues;
    protected long expire;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Value<Object> getValue(String cacheName, String keyValue) {
        Cache cache = getCache(cacheName);
        return Value.of(cache.get(keyValue, Value.class));
    }

    @Override
    public void setCacheProperties(CacheProperties.CachePoolProperties cachePoolProperties) {
        this.cachePoolProperties = cachePoolProperties;
        this.expire = cachePoolProperties.getExpire();
    }

    @Override
    public void setValue(String cacheName, String keyValue, Value<Object> value) {
        Cache cache = getCache(cacheName);
        cache.put(keyValue, value);
    }

    @Override
    public void evict(String cacheName, String keyValue) {
        Cache cache = getCache(cacheName);
        cache.evictIfPresent(keyValue);
    }

    @Override
    public void clear(String cacheName) {
        Cache cache = getCache(cacheName);
        cache.clear();
    }

    /**
     * 初始化缓存
     *
     * @param cacheName         名称
     * @param isCacheNullValues 是否允许为空
     * @return 初始化缓存
     */
    abstract public Cache createCache(String cacheName, boolean isCacheNullValues);

    /**
     * 获取缓存
     *
     * @param cacheName 缓存名称
     * @return 缓存
     */
    private Cache getCache(String cacheName) {
        try {
            return cacheMap.computeIfAbsent(cacheName, it -> {
                Cache cache = createCache(cacheName, cacheNullValues);
                if (null != cache) {
                    return cache;
                }
                log.warn("{}缓存创建缓存失败, 开始降级未内存缓存", getBeanName());
                return createCurrentCache(cacheName);
            });
        } catch (Exception e) {
            log.warn("{}缓存创建缓存失败, 开始降级未内存缓存, 失败原因: {}", getBeanName(), e.getMessage());
            return cacheMap.computeIfAbsent(cacheName, it -> createCurrentCache(cacheName));
        }
    }

    /**
     * 本地缓存
     *
     * @param cacheName 缓存
     * @return 缓存
     */
    protected Cache createCurrentCache(String cacheName) {
        return new ConcurrentMapCache(cacheName, cacheNullValues);
    }

    @Override
    public void isCacheNullValues(boolean cacheNullValues) {
        this.cacheNullValues = cacheNullValues;
    }
}
