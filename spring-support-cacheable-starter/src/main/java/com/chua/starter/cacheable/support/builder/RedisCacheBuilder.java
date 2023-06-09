package com.chua.starter.cacheable.support.builder;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.annotation.Resource;

/**
 * 缓存构建器
 *
 * @author CH
 */
@Spi("redis")
public class RedisCacheBuilder implements CacheBuilder {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Cache build(String name, CacheProperties cacheProperties, org.springframework.boot.autoconfigure.cache.CacheProperties cacheProperties1) {
        RedisCacheManager redisCacheManager = new RedisCacheManager(RedisCacheWriter
                .lockingRedisCacheWriter(redisConnectionFactory), RedisCacheConfiguration.defaultCacheConfig(), cacheProperties.isCacheNullValues());
        redisCacheManager.afterPropertiesSet();
        return redisCacheManager.getCache(name);
    }
}
