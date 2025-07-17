package com.chua.starter.plugin.config;

import com.chua.starter.plugin.entity.PluginBlackWhiteList;
import com.chua.starter.plugin.entity.PluginRateLimitConfig;
import com.chua.starter.plugin.entity.PluginXssConfig;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.StoreFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储配置
 * 
 * @author CH
 * @since 2025/1/16
 */
@Configuration
@RequiredArgsConstructor
public class StoreConfiguration {

    private final StoreFactory storeFactory;

    /**
     * 限流配置存储Bean
     */
    @Bean
    public PersistenceStore<PluginRateLimitConfig, Long> rateLimitConfigStore() {
        return storeFactory.createRateLimitConfigStore();
    }

    /**
     * 黑白名单存储Bean
     */
    @Bean
    public PersistenceStore<PluginBlackWhiteList, Long> blackWhiteListStore() {
        return storeFactory.createBlackWhiteListStore();
    }

    /**
     * XSS配置存储Bean
     */
    @Bean
    public PersistenceStore<PluginXssConfig, Long> xssConfigStore() {
        return storeFactory.createXssConfigStore();
    }

}
