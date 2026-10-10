package com.chua.starter.ai.support.configuration.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * AI 模块缓存自动配置。
 *
 * <p>本应用历史上从未启用声明式缓存：spring-support-cache-starter 的多级缓存管理器
 * 需 {@code plugin.cache.enabled=true}，其 {@code @EnableCaching} 兜底入口也未被
 * 自动装配，导致所有 {@code @Cacheable} 被静默忽略。本配置统一启用声明式缓存：</p>
 * <ul>
 *   <li>未显式指定 cacheManager 的缓存（如全量指标 aiModelMetrics、定价 aiPricing）
 *       走 {@link Primary} 的本地 Caffeine，只读参考数据、快速稳定；</li>
 *   <li>代码中按 TTL 命名引用的 Redis 缓存管理器 {@code redis-cache--1}（永久）与
 *       {@code redis-cache-600}（10 分钟）连本地 Redis 提供。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@AutoConfiguration
@EnableCaching
@ConditionalOnClass(CacheManager.class)
@ConditionalOnProperty(value = "chua.ai.cache.enabled", havingValue = "true", matchIfMissing = true)
public class AiCacheAutoConfiguration {

    /**
     * 默认缓存管理器：本地 Caffeine（未显式指定 cacheManager 的 @Cacheable 使用）。
     *
     * @return Caffeine 缓存管理器
     */
    @Bean
    @Primary
    public CacheManager aiCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(128)
                .maximumSize(20_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());
        // 与 @Cacheable(unless = "#result == null") 保持一致，不缓存空值
        manager.setAllowNullValues(false);
        return manager;
    }

    /**
     * Redis 永久缓存管理器，Bean 名 {@code redis-cache--1}。
     *
     * @param connectionFactory Redis 连接工厂
     * @return Redis 缓存管理器
     */
    @Bean("redis-cache--1")
    @ConditionalOnClass(RedisConnectionFactory.class)
    public CacheManager redisCacheAlways(RedisConnectionFactory connectionFactory) {
        // TTL=0 在 RedisCacheManager 中表示永久
        return buildRedisCacheManager(connectionFactory, Duration.ZERO);
    }

    /**
     * Redis 10 分钟缓存管理器，Bean 名 {@code redis-cache-600}。
     *
     * @param connectionFactory Redis 连接工厂
     * @return Redis 缓存管理器
     */
    @Bean("redis-cache-600")
    @ConditionalOnClass(RedisConnectionFactory.class)
    public CacheManager redisCacheMin(RedisConnectionFactory connectionFactory) {
        return buildRedisCacheManager(connectionFactory, Duration.ofSeconds(600));
    }

    /**
     * 构建指定 TTL 的 Redis 缓存管理器。
     *
     * <p>必须显式开启 {@code immediateWrites}：Spring Data Redis 4.x 的缓存写操作默认异步执行，
     * 实测 {@code RedisCache#evict} 只另建一条 Lettuce 连接、始终不下发 {@code DEL}，
     * 失败又被 {@code CompletableFuture} 静默吞掉，导致改完配置读接口永久返回旧值。</p>
     *
     * <p>同理不启用 {@code transactionAware()}：装饰器会把失效再推迟到事务提交之后，
     * 而配置写入方法均在 {@code @Transactional} 内执行。</p>
     *
     * @param connectionFactory Redis 连接工厂
     * @param ttl               过期时间
     * @return Redis 缓存管理器
     */
    private RedisCacheManager buildRedisCacheManager(RedisConnectionFactory connectionFactory, Duration ttl) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer()))
                .disableCachingNullValues()
                .entryTtl(ttl);
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory,
                writer -> writer.immediateWrites(true));
        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(config)
                .build();
    }

    /**
     * 构建缓存值序列化器。
     *
     * <p>Spring Data Redis 4.x 已将无参构造与 {@code configure(...)} 标记为待移除，
     * 改为显式构建 {@link ObjectMapper}：注册 {@link JavaTimeModule}（缓存实体的
     * {@code LocalDateTime} 等字段）、关闭时间戳输出，并开启 NON_FINAL 默认多态
     * （以 {@code @class} 属性携带类型，与历史缓存数据兼容），再传入构造器。</p>
     *
     * @return 支持 {@code java.time} 的值序列化器
     */
    @SuppressWarnings("removal")
    private RedisSerializer<Object> valueSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
