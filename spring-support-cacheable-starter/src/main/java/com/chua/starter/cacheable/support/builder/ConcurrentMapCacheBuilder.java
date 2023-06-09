package com.chua.starter.cacheable.support.builder;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * 缓存构建器
 *
 * @author CH
 */
@Spi("mem")
public class ConcurrentMapCacheBuilder implements CacheBuilder {

    @Override
    public Cache build(String name, CacheProperties cacheProperties, org.springframework.boot.autoconfigure.cache.CacheProperties cacheProperties1) {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager();
        manager.setAllowNullValues(cacheProperties.isCacheNullValues());
        return manager.getCache(name);
    }
}
