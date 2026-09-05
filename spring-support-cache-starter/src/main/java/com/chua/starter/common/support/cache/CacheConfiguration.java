package com.chua.starter.common.support.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.constant.CacheConstant.*;

/**
 * 缓存自动配置
 * <p>
 * 根据 {@link CacheProperties} 配置动态创建 Caffeine、Redis 以及多级缓存管理器。
 * 支持自定义缓存管理器 Bean 名称，便于 MultiLevelCacheManager 按名称查找。
 * </p>
 *
 * @since 2024/7/22
 * @author CH
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnClass(name = "org.springframework.data.redis.serializer.RedisSerializer")
@AutoConfigureAfter(name = "com.chua.starter.redis.support.CacheConfiguration")
public class CacheConfiguration {

    /**
     * Caffeine 缓存管理器
     * <p>
     * 当未显式定义 caffeineCacheManager Bean 时，根据 CacheProperties 创建。
     * </p>
     */
    @Bean(name = CacheProperties.CAFFEINE_BEAN_NAME)
    @ConditionalOnMissingBean(name = CacheProperties.CAFFEINE_BEAN_NAME)
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
    public CacheManager caffeineCacheManager(CacheProperties properties) {
        CacheProperties.CaffeineProperties caffeineProps = properties.getCaffeine();
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .initialCapacity(caffeineProps.getInitialCapacity())
                .maximumSize(caffeineProps.getMaximumSize());

        if (caffeineProps.isRecordStats()) {
            builder.recordStats();
        }

        if (caffeineProps.getExpireAfterWrite() > 0) {
            builder.expireAfterWrite(caffeineProps.getExpireAfterWrite(), TimeUnit.SECONDS);
        }
        if (caffeineProps.getExpireAfterAccess() > 0) {
            builder.expireAfterAccess(caffeineProps.getExpireAfterAccess(), TimeUnit.SECONDS);
        }
        if (caffeineProps.isWeakKeys()) {
            builder.weakKeys();
        }
        if (caffeineProps.isWeakValues()) {
            builder.weakValues();
        }

        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(builder);
        manager.setAllowNullValues(properties.isCacheNullValues());
        return manager;
    }

    /**
     * Redis 缓存管理器
     * <p>
     * 当未显式定义 redisCacheManager Bean 时，根据 CacheProperties 创建。
     * </p>
     */
    @Bean(name = CacheProperties.REDIS_BEAN_NAME)
    @ConditionalOnMissingBean(name = CacheProperties.REDIS_BEAN_NAME)
    @ConditionalOnClass({RedisConnectionFactory.class, RedisCacheManager.class})
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
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

    /**
     * 多级缓存管理器
     * <p>
     * 作为主缓存管理器，按配置的层级顺序组合 Caffeine、Redis 等。
     * 当未显式定义 multiLevelCacheManager Bean 时创建。
     * </p>
     */
    @Primary
    @Bean(name = CACHE_MANAGER_FOR_MULTI)
    @ConditionalOnMissingBean(name = CACHE_MANAGER_FOR_MULTI)
    @ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
    public MultiLevelCacheManager multiLevelCacheManager(ApplicationContext applicationContext) {
        MultiLevelCacheManager manager = new MultiLevelCacheManager();
        manager.setApplicationContext(applicationContext);
        manager.afterPropertiesSet();
        return manager;
    }

    /**
     * 租户缓存键生成器
     * <p>
     * 用于生成包含租户、应用、平台等上下文的缓存键。
     * </p>
     */
    @Bean("tenantedKeyGenerator")
    @ConditionalOnMissingBean(name = "tenantedKeyGenerator")
    public TenantedKeyGenerator tenantedKeyGenerator(
            ApplicationContext applicationContext,
            CacheProperties properties,
            java.util.Optional<TenantIdProvider> tenantIdProvider) {
        TenantedKeyGenerator generator = new TenantedKeyGenerator();
        generator.setApplicationContext(applicationContext);
        generator.setKeyProperties(properties.getKey());
        tenantIdProvider.ifPresent(generator::setTenantIdProvider);
        return generator;
    }

    /**
     * 兼容旧版的自定义缓存键生成器 Bean（返回 SimpleKeyGenerator）。
     *
     * <p>保留供既有 @Cacheable(keyGenerator = "customTenantedKeyGenerator") 引用兼容。</p>
     */
    @Bean("customTenantedKeyGenerator")
    @ConditionalOnMissingBean(name = "customTenantedKeyGenerator")
    public org.springframework.cache.interceptor.KeyGenerator customTenantedKeyGenerator() {
        return new org.springframework.cache.interceptor.SimpleKeyGenerator();
    }

    /**
     * 自定义缓存注解解析器
     * <p>
     * 支持 key 和 keyGenerator 同时使用，默认使用 tenantedKeyGenerator。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(CacheCustomAnnotationParser.class)
    public CacheCustomAnnotationParser cacheCustomAnnotationParser() {
        return new CacheCustomAnnotationParser();
    }

/**
 * 缓存操作模板
 * <p>
 * 提供手动缓存操作方法，内部自动使用 tenantedKeyGenerator 拼接完整缓存键，
 * 保证手动操作与 {@code @Cacheable} 等注解生成的 key 一致。
 * </p>
 */
@Bean
@ConditionalOnMissingBean(CacheTemplate.class)
@ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
public CacheTemplate cacheTemplate(
        MultiLevelCacheManager multiLevelCacheManager,
        TenantedKeyGenerator tenantedKeyGenerator) {
    return new CacheTemplate(multiLevelCacheManager, tenantedKeyGenerator);
}

/**
 * 缓存操作源（覆盖默认，使用自定义解析器）
     * <p>无条件创建并标记 @Primary，确保 CacheInterceptor 始终使用
     * 支持 key 与 keyGenerator 共用的 CacheCustomAnnotationParser；
     * 若依赖 @ConditionalOnMissingBean 会被 @EnableCaching 默认的标准解析器抢注，
     * 导致 key+keyGenerator 同时使用时报 IllegalStateException。</p>
     */
    @Bean
    @Primary
    public org.springframework.cache.interceptor.CacheOperationSource customCacheOperationSource(
            CacheCustomAnnotationParser parser) {
        return new org.springframework.cache.annotation.AnnotationCacheOperationSource(parser);
    }

    /**
     * 注册缓存模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public com.chua.starter.common.support.serviceinfo.ServiceInfoContributor cacheServiceInfoContributor() {
        return registry -> registry.registerFlag("cache", "缓存", true, "缓存模块");
    }
}