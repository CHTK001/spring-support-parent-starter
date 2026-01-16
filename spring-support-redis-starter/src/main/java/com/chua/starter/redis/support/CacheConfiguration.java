package com.chua.starter.redis.support;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

import static com.chua.starter.common.support.constant.CacheConstant.*;
import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE_HOUR;
import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE_MIN;
import static com.chua.starter.common.support.jackson.configuration.JacksonConfiguration.createObjectMapper;

/**
 * 缓存配置
 *
 * @author CH
 */
@Configuration
public class CacheConfiguration {

    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean(REDIS_CACHE_MIN)
    public CacheManager systemCacheManager600(RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, 600);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean(REDIS_CACHE_HALF_MINUTES)
    public CacheManager systemCacheManager30(RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, 30);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean(REDIS_CACHE_TEN_SECOND)
    public CacheManager systemCacheManager10(RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, 10);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean(REDIS_CACHE_MINUTES)
    public CacheManager systemCacheManager60(RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, 60);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE_HOUR})
    public CacheManager systemCacheManager( RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, 3600);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE_ALWAYS})
    public CacheManager systemCacheManagerAlways( RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, -1);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE_ONE_DAY})
    public CacheManager redis86400CacheManager(RedisConnectionFactory factory) {
        return createRedisCacheManager(null, factory, 86400);
    }
    /**
     * 创建redis缓存管理器
     *
     * @param om
     * @param factory 工厂
     * @param seconds 秒
     * @return {@link CacheManager}
     */
    public static CacheManager createRedisCacheManager(ObjectMapper om, RedisConnectionFactory factory, int seconds) {
        RedisSerializer<String> redisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(
                null == om ? createObjectMapper(true, false) : om);

        // 配置序列化（解决乱码的问题）,过期时间600秒
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(seconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer))
                .prefixCacheNameWith("CACHE:")
                .computePrefixWith(name -> name + ":")
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .allowCreateOnMissingCache(true)
                .build();
    }


}
