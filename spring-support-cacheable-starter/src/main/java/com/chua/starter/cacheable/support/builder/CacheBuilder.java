package com.chua.starter.cacheable.support.builder;

import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.cache.Cache;

/**
 * 缓存构建器
 *
 * @author CH
 */
public interface CacheBuilder {
    /**
     * 缓存
     *
     * @param name             name
     * @param cacheProperties  prop
     * @param cacheProperties1 prop1
     * @return 缓存
     */
    Cache build(String name, CacheProperties cacheProperties, org.springframework.boot.autoconfigure.cache.CacheProperties cacheProperties1);
}
