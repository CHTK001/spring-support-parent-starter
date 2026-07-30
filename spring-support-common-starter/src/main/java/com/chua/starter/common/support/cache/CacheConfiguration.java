package com.chua.starter.common.support.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缓存配置
 * <p>
 * 提供默认缓存管理器和系统级多级缓存管理器。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration extends CachingConfigurerSupport  {
    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    public static final String DEFAULT_CACHE_MANAGER = "default";
    public static final String SYSTEM_CACHE_MANAGER = "system";

    @Override
    public CacheManager cacheManager() {
        return cachingProvider();
    }

    /**
     * 默认缓存管理器（Caffeine）
     */
    @Bean(DEFAULT_CACHE_MANAGER)
    @Primary
    public CacheManager defaultCacheManager() {
        return cachingProvider();
    }

    /**
     * 创建 Caffeine 缓存管理器的新实例，避免静态单例被共享修改。
     */
    private static CaffeineCacheManager cachingProvider() {
        return new CaffeineCacheManager();
    }

    /**
     * 系统级多级缓存管理器
     */
    @Bean(SYSTEM_CACHE_MANAGER)
    public CacheManager systemCacheManager() {
        return new MultiLevelCacheManager();
    }

    @Bean("customTenantedKeyGenerator")
    public KeyGenerator customTenantedKeyGenerator() {
        return new SimpleKeyGenerator();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * 缓存错误处理器
     */
    static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggingCacheErrorHandler.class);
        
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.error("缓存读取错误: cache={}, key={}", 
                    cache == null ? "unknown" : cache.getName(), key, exception);
            super.handleCacheGetError(exception, cache, key);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.error("缓存写入错误: cache={}, key={}", 
                    cache == null ? "unknown" : cache.getName(), key, exception);
            super.handleCachePutError(exception, cache, key, value);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.error("缓存清除错误: cache={}, key={}", 
                    cache == null ? "unknown" : cache.getName(), key, exception);
            super.handleCacheEvictError(exception, cache, key);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.error("缓存清空错误: cache={}", 
                    cache == null ? "unknown" : cache.getName(), exception);
            super.handleCacheClearError(exception, cache);
        }
    }
}
