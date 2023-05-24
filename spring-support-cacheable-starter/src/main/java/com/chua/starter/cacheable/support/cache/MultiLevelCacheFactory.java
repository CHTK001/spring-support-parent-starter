package com.chua.starter.cacheable.support.cache;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.value.Value;
import com.chua.starter.cacheable.support.handler.CacheManagerHandler;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import com.google.common.base.Strings;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存工厂
 *
 * @author CH
 * @since 2022/8/10 13:39
 */
public class MultiLevelCacheFactory implements ApplicationContextAware {
    private final CacheProperties cacheProperties;
    private ApplicationContext applicationContext;
    private static final Map<String, CacheManagerHandler> CACHE_MANAGER = new ConcurrentHashMap<>();

    private static final List<CacheManagerHandler> REAL = new LinkedList<>();

    static {
        List<CacheManagerHandler> subOfObject = ServiceProvider.of(CacheManagerHandler.class).collect();
        for (CacheManagerHandler cacheManagerHandler : subOfObject) {
            CACHE_MANAGER.put(cacheManagerHandler.getBeanName(), cacheManagerHandler);
        }
    }

    public MultiLevelCacheFactory(CacheProperties cacheProperties, ApplicationContext applicationContext) {
        this.cacheProperties = cacheProperties;
        this.applicationContext = applicationContext;
        this.initial();
    }

    /**
     * 初始化
     */
    private void initial() {
        List<CacheProperties.CachePoolProperties> multiCache = cacheProperties.getMultiCache();
        for (CacheProperties.CachePoolProperties cachePoolProperties : multiCache) {
            String type = cachePoolProperties.getType();
            CacheManagerHandler cacheManagerHandler = CACHE_MANAGER.get(type);
            if (null == cacheManagerHandler) {
                continue;
            }

            cacheManagerHandler.setApplicationContext(applicationContext);
            cacheManagerHandler.setCacheProperties(cachePoolProperties);
            cacheManagerHandler.isCacheNullValues(cacheProperties.isCacheNullValues());
            REAL.add(cacheManagerHandler);
        }
    }

    /**
     * 是否允许空
     *
     * @return 是否允许空
     */
    public boolean isCacheNullValues() {
        return cacheProperties.isCacheNullValues();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public String cachePrefix(String key) {
        return Strings.isNullOrEmpty(cacheProperties.getCachePrefix()) ? key : cacheProperties.getCachePrefix() + ":" + key;
    }

    /**
     * 获取值
     *
     * @param cacheName 缓存名称
     * @param keyValue  key
     * @return 值
     */
    public Value<Object> getValue(String cacheName, String keyValue) {
        Value<Object> value = Value.of(null);
        List<CacheManagerHandler> list = new ArrayList<>(REAL.size());
        for (CacheManagerHandler handler : REAL) {
            value = handler.getValue(cacheName, keyValue);
            if (!value.isNull()) {
                break;
            }
            list.add(handler);
        }

        if (!value.isNull() && !list.isEmpty()) {
            for (CacheManagerHandler cacheManagerHandler : list) {
                cacheManagerHandler.setValue(cacheName, keyValue, value);
            }
        }

        return value;
    }

    /**
     * 获取值
     *
     * @param cacheName 缓存名称
     * @param keyValue  key
     * @param value     值
     */
    public void setValue(String cacheName, String keyValue, Object value) {
        for (CacheManagerHandler handler : REAL) {
            handler.setValue(cacheName, keyValue, Value.of(value));
        }
    }

    /**
     * 删除值
     *
     * @param cacheName 缓存名称
     * @param keyValue  key
     */
    public void evict(String cacheName, String keyValue) {
        for (CacheManagerHandler handler : REAL) {
            handler.evict(cacheName, keyValue);
        }
    }

    /**
     * 清空
     *
     * @param cacheName 缓存名称
     */
    public void clear(String cacheName) {
        for (CacheManagerHandler handler : REAL) {
            handler.clear(cacheName);
        }
    }
}
