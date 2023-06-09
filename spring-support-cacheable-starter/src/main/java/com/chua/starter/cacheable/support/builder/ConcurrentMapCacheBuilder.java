package com.chua.starter.cacheable.support.builder;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.cacheable.support.cache.ExpireConcurrentMapCache;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.cache.Cache;

/**
 * 缓存构建器
 *
 * @author CH
 */
@Spi("mem")
public class ConcurrentMapCacheBuilder implements CacheBuilder {

    @Override
    public Cache build(String name, CacheProperties cacheProperties, org.springframework.boot.autoconfigure.cache.CacheProperties cacheProperties1) {
        return new ExpireConcurrentMapCache(name, cacheProperties.getExpire());
    }
}
