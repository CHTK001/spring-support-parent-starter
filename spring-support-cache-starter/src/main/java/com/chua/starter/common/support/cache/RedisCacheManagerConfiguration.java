package com.chua.starter.common.support.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

import static com.chua.starter.common.support.constant.CacheConstant.TTL_NEVER_EXPIRE;

/**
* Redis 缓存自动配置 (独立类)。
*
* <p>独立于 {@link CacheConfiguration}, 仅在 classpath 存在 spring-data-redis 时生效,
* 避免缺少 Redis 依赖的应用启动时内省 {@link CacheConfiguration} 报
* {@code NoClassDefFoundError}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@AutoConfiguration
@ConditionalOnClass(RedisSerializer.class)
@ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
public class RedisCacheManagerConfiguration {

    /**
    * Redis 缓存管理器
    * <p>
    * 当未显式定义 Redis缓存管理器 Bean 时，根据 缓存属性 创建。
    * </p>
    *
    * @param properties       缓存配置
    * @param connectionFactory Redis 连接工厂
    * @param objectMapper      JSON 序列化器
    * @return Redis 缓存管理器
     */
    @Bean(name = CacheProperties.REDIS_BEAN_NAME)
    @ConditionalOnMissingBean(name = CacheProperties.REDIS_BEAN_NAME)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager redisCacheManager(
            CacheProperties properties,
            RedisConnectionFactory connectionFactory,
            @Lazy ObjectMapper objectMapper) {

        CacheProperties.RedisProperties redisProps = properties.getRedis();

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        if (!properties.isCacheNullValues()) {
            config = config.disableCachingNullValues();
        }

        if (redisProps.getTtl() > 0) {
            config = config.entryTtl(Duration.ofSeconds(redisProps.getTtl()));
        } else if (redisProps.getTtl() == TTL_NEVER_EXPIRE) {
            config = config.entryTtl(Duration.ZERO);
        }

        if (redisProps.isUseKeyPrefix()) {
            String prefix = redisProps.getKeyPrefix();
            config = config.prefixCacheNameWith(prefix);
            config = config.computePrefixWith(name -> name + redisProps.getSeparator());
        }

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .allowCreateOnMissingCache(true)
                .build();
    }
}