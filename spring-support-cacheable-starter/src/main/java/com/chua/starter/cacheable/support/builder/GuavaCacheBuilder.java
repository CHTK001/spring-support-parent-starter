package com.chua.starter.cacheable.support.builder;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.cacheable.support.manager.GuavaCache;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.cache.Cache;

import java.util.concurrent.TimeUnit;

import static com.google.common.cache.CacheBuilder.newBuilder;

/**
 * 缓存构建器
 *
 * @author CH
 */
@Spi("guava")
public class GuavaCacheBuilder implements CacheBuilder {

    @Override
    public Cache build(String name, CacheProperties cacheProperties, org.springframework.boot.autoconfigure.cache.CacheProperties cacheProperties1) {
        return new GuavaCache(name, newBuilder().expireAfterWrite(cacheProperties.getExpire(), TimeUnit.SECONDS).build(), cacheProperties.isCacheNullValues());
    }
}
