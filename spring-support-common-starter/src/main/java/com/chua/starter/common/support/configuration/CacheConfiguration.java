package com.chua.starter.common.support.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.annotation.Bean;

/**
 *  Cache
 * @author CH
 */
public class CacheConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheManager getCacheManager() {
        return new EhCacheCacheManager();
    }
}
