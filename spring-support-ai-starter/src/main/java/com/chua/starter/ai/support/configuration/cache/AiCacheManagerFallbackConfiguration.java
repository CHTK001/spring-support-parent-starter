package com.chua.starter.ai.support.configuration.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * AI 缓存管理器降级配置。
 *
 * <p>当主缓存管理器不可用时（如 Redis 未连接），自动降级为本地 Caffeine 缓存，
 * 确保 AI 模块的缓存功能始终保持可用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
public class AiCacheManagerFallbackConfiguration {

    /**
     * AI 降级缓存管理器。
     *
     * <p>使用 Caffeine 本地缓存，TTL 默认 5 分钟，最大 1000 条。
     *
     * @return CacheManager 实例
     */
    @Bean("aiFallbackCacheManager")
    @ConditionalOnMissingBean(name = "aiFallbackCacheManager")
    public CacheManager aiFallbackCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofMinutes(5))
                .maximumSize(1000)
                .recordStats());
        log.info("[AiCacheManagerFallback] 创建 AI 降级缓存管理器（Caffeine, TTL=5min, max=1000）");
        return cacheManager;
    }
}