package com.chua.starter.vuesql.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * websql
 * @author CH
 */
@ComponentScan("com.chua.starter.vuesql")
public class WebSqlConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public CacheManager getCacheManager() {
        return new EhCacheCacheManager();
    }
}
