package com.chua.starter.common.support.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 *  Cache
 * @author CH
 */
public class CacheConfiguration {

    public static final String DEFAULT_CACHE_MANAGER = "default-cache-manager";
    @Bean(DEFAULT_CACHE_MANAGER)
    public CacheManager getDefaultCacheManager() {
        return new CaffeineCacheManager();
    }
    @Bean
    @Lazy
    @ConditionalOnMissingBean
    public CacheManager getCacheManager() {
        return new EhCacheCacheManager();
    }


}
