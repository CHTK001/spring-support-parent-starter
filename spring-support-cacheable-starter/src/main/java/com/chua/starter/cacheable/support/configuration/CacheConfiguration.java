package com.chua.starter.cacheable.support.configuration;

import com.chua.starter.cacheable.support.MultiLevelCacheManager;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * com.chua.starter.common.configuration.CacheConfiguration
 *
 * @author CH
 * @since 2022/8/10 11:46
 */
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {


    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "multiLevelCacheManager")
    public MultiLevelCacheManager multiLevelCacheManager(CacheProperties cacheProperties) {
        return new MultiLevelCacheManager(cacheProperties);
    }

}
