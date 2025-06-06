package com.chua.starter.common.support.configuration;

import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheOperationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 缓存配置
 *
 * @author CH
 * @since 2025/6/6 9:57
 */
@Configuration
@EnableCaching
public class CacheProxyCachingConfiguration {
    @Bean
    @Primary
    public CacheOperationSource customCacheOperationSource() {
        return new AnnotationCacheOperationSource(cacheAnnotationParser());
    }

    @Bean
    public CacheCustomCacheAnnotationParser cacheAnnotationParser() {
        return new CacheCustomCacheAnnotationParser();
    }


}
