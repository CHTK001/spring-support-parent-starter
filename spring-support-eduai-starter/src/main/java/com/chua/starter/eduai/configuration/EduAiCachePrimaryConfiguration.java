package com.chua.starter.eduai.configuration;

import com.chua.starter.redis.support.CacheConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 解决 spring-api-support-common-system-starter 4.0.0.51 缺少 CacheManager @Primary 候选的问题
 * <p>
 * 4.0.0.51 将 {@code @Primary} 从 {@code DefaultCacheManagerConfiguration$RedisCacheManagerConfiguration
 * #defaultCacheManager()} 移到了 {@code h2} profile 的 {@code primaryCacheManager()}（NoOpCacheManager）上。
 * 应用连接 MySQL 时 {@code h2} 配置类不加载，所以没有任何 {@code @Primary} 的 {@code CacheManager}
 * 候选，Spring 无法为所有无 {@code @Qualifier} 的 {@code @Autowired CacheManager} 字段选出唯一的 bean，
 * 启动直接报 "required a single bean, but 10 were found" 而失败。
 * <p>
 * 本类使用框架 redis 缓存的构建方法 {@code CacheConfiguration#createRedisCacheManager(ObjectMapper,
 * RedisConnectionFactory, int)} 创建唯一的 {@code @Primary} cacheManager（Redis 源，1 小时过期），
 * 并让 {@code defaultCacheManager} / {@code cacheWithRedis} 等透传 bean 也有迹可循。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Configuration
public class EduAiCachePrimaryConfiguration {

    /**
     * 教育 AI 应用的唯一默认 CacheManager（Redis 源）。
     *
     * @param objectMapper     框架的 Jackson ObjectMapper（类型唯一，可注入）
     * @param connectionFactory 框架的 RedisConnectionFactory（类型唯一，可注入）
     * @return Redis 源 CacheManager，设置 TTL
     */
    @Bean
    @Primary
    public CacheManager cacheManager(ObjectMapper objectMapper, RedisConnectionFactory connectionFactory) {
        // TTL（秒）：与框架 systemCacheManager 同口径
        return CacheConfiguration.createRedisCacheManager(objectMapper, connectionFactory, 3600);
    }
}
