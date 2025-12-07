package com.chua.starter.common.support.cache.configuration;

import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheOperationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 缓存代理配置
 * <p>
 * 使用自定义的缓存注解解析器，支持 key �?keyGenerator 同时使用�?
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/6/6
 */
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

