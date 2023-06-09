package com.chua.starter.cacheable.support.builder;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.ehcache.EhCacheCacheManager;

/**
 * 缓存构建器
 *
 * @author CH
 */
@Spi("ehcache")
public class EhCacheCacheBuilder implements CacheBuilder {

    @Override
    public Cache build(String name, CacheProperties cacheProperties, org.springframework.boot.autoconfigure.cache.CacheProperties cacheProperties1) {
        EhCacheCacheManager ehCacheCacheManager = new EhCacheCacheManager();
        ehCacheCacheManager.afterPropertiesSet();
        return ehCacheCacheManager.getCache(name);
    }
}
