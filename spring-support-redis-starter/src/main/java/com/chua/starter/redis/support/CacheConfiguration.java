package com.chua.starter.redis.support;


import com.chua.common.support.converter.Converter;
import com.chua.common.support.utils.ClassUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ONE_DAY;
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
     * 创建jackson对象
     * @return JacksonObjectWriter
     */
    public JacksonObjectWriter createJacksonObjectWriter() {
        return (mapper, source) -> {
            Map<String, Object> map = new HashMap<>(2);
            map.put("type", source.getClass().getTypeName());
            map.put("data", source);
            return mapper.writeValueAsBytes(map);
        };
    }

    /**
     * 创建jackson对象
     * @return JacksonObjectReader
     */
    public JacksonObjectReader createJacksonObjectReader() {
        return (mapper, source, type) -> {
            Object object = mapper.readValue(source, 0, source.length, type);
            if(type.getRawClass() != Object.class) {
                return object;
            }
            if(object instanceof Map map) {
                Object type1 = map.get("type");
                if(null == type1) {
                    return map;
                }
                Object object1 = map.get("data");
                return Converter.convertIfNecessary(object1, ClassUtils.toType(type1));
            }
            return object;
        };
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean(REDIS_CACHE_MIN)
    public CacheManager systemCacheManager600(ObjectMapper om, RedisConnectionFactory factory) {
        return createRedisCacheManager(om, factory, 600);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE_HOUR})
    public CacheManager systemCacheManager(ObjectMapper om, RedisConnectionFactory factory) {
        return createRedisCacheManager(om, factory, 3600);
    }
    /**
     * 系统缓存经理
     *
     * @param factory 工厂
     * @return {@link CacheManager}
     */
    @Bean({REDIS_CACHE_ONE_DAY})
    public CacheManager redis86400CacheManager(ObjectMapper om, RedisConnectionFactory factory) {
        return createRedisCacheManager(om, factory, 86400);
    }
    /**
     * 创建redis缓存管理器
     *
     * @param om
     * @param factory 工厂
     * @param seconds 秒
     * @return {@link CacheManager}
     */
    private CacheManager createRedisCacheManager(ObjectMapper om, RedisConnectionFactory factory, int seconds) {
        RedisSerializer<String> redisSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(om, TypeFactory.defaultInstance().constructType(Object.class), createJacksonObjectReader(), createJacksonObjectWriter());

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
