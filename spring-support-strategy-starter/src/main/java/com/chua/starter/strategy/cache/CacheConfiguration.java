package com.chua.starter.strategy.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;

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
@Slf4j
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enable", havingValue = "true")
public class CacheConfiguration extends CachingConfigurerSupport {

    public static final String DEFAULT_CACHE_MANAGER = "default";
    public static final String SYSTEM_CACHE_MANAGER = "system";

    private static final CacheManager DEFAULT_MANAGER = new CaffeineCacheManager();

    @Override
    public CacheManager cacheManager() {
        return DEFAULT_MANAGER;
    }

    /**
     * 默认缓存管理器（Caffeine）
     */
    @Bean(DEFAULT_CACHE_MANAGER)
    public CacheManager defaultCacheManager() {
        return DEFAULT_MANAGER;
    }

    /**
     * 系统级多级缓存管理器
     */
    @Bean(SYSTEM_CACHE_MANAGER)
    public CacheManager systemCacheManager() {
        return new MultiLevelCacheManager();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * 缓存错误处理器
     */
    static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {
        
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
