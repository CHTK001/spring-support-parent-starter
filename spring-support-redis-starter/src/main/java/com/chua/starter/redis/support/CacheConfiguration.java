package com.chua.starter.redis.support;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE;
import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE_HOUR;
import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE_MIN;

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
        return createRedisCacheManager(factory, 600);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE_HOUR})
    public CacheManager systemCacheManager(RedisConnectionFactory factory) {
        return createRedisCacheManager(factory, 3600);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE})
    public CacheManager redis86400CacheManager(RedisConnectionFactory factory) {
        return createRedisCacheManager(factory, 86400);
    }
    /**
     * 创建redis缓存管理器
     *
     * @param factory 工厂
     * @param seconds 秒
     * @return {@link CacheManager}
     */
    private CacheManager createRedisCacheManager(RedisConnectionFactory factory, int seconds) {
        RedisSerializer<String> redisSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);

        //解决查询缓存转换异常的问题
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        // 配置序列化（解决乱码的问题）,过期时间600秒
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(seconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }


}
