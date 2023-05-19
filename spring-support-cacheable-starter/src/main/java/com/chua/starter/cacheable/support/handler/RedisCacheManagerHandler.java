package com.chua.starter.cacheable.support.handler;

import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 处理器
 *
 * @author CH
 * @since 2022/8/10 13:55
 */
public class RedisCacheManagerHandler extends AbstractCacheManagerHandler {

    @Override
    public String getBeanName() {
        return "redis";
    }

    @Override
    public Cache createCache(String cacheName, boolean isCacheNullValues) {
        try {
            RedisTemplate redisTemplate = applicationContext.getBean(RedisTemplate.class);
            RedisCacheManager redisCacheManager = RedisCacheManager.builder(redisTemplate.getConnectionFactory())
                    .build();
            return redisCacheManager.getCache(cacheName);
        } catch (Throwable ignored) {
            try {
                throw new Exception(ignored.getMessage());
            } catch (Throwable ignore) {
            }
        }
        return null;
    }
}
