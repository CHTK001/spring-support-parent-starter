package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.cache.CustomCacheManager;
import com.chua.starter.common.support.constant.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;

/**
 * Cache缓存
 *
 * @author CH
 */
@Slf4j
public class CacheConfiguration extends CachingConfigurerSupport implements Constant {

    public static final String DEFAULT_CACHE_MANAGER = "default";

    private static final CacheManager CACHE_MANAGER = new CaffeineCacheManager();

    public CacheConfiguration() {
    }

    @Override
    public CacheManager cacheManager() {
        return CACHE_MANAGER;
    }

    @Bean(DEFAULT_CACHE_MANAGER)
    public CacheManager getDefaultCacheManager() {
        return CACHE_MANAGER;
    }

    @Bean({SYSTEM})
    public CacheManager systemCacheManager() {
        return new CustomCacheManager();
    }

    /**
     * 如果cache出错， 我们会记录在日志里，方便排查，比如反序列化异常
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /* non-public */ static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.error(String.format("cacheName:%s,cacheKey:%s",
                    cache == null ? "unknown" : cache.getName(), key), exception);
            super.handleCacheGetError(exception, cache, key);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
                                        Object value) {
            log.error(String.format("cacheName:%s,cacheKey:%s",
                    cache == null ? "unknown" : cache.getName(), key), exception);
            super.handleCachePutError(exception, cache, key, value);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.error(String.format("cacheName:%s,cacheKey:%s",
                    cache == null ? "unknown" : cache.getName(), key), exception);
            super.handleCacheEvictError(exception, cache, key);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.error(String.format("cacheName:%s", cache == null ? "unknown" : cache.getName()),
                    exception);
            super.handleCacheClearError(exception, cache);
        }
    }

}
