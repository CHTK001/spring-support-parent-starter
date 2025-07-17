package com.chua.starter.plugin.config;

import com.chua.starter.plugin.entity.BlackWhiteList;
import com.chua.starter.plugin.entity.PluginNodeLoggerConfig;
import com.chua.starter.plugin.entity.PluginXssAttackLog;
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
    // @Bean
    // public PersistenceStore<RateLimitConfig, Long> rateLimitConfigStore() {
    // return storeFactory.createRateLimitConfigStore();
    // }

    /**
     * 黑白名单存储Bean
     */
    @Bean
    public PersistenceStore<BlackWhiteList, Long> blackWhiteListStore() {
        return storeFactory.createBlackWhiteListStore();
    }

    /**
     * XSS配置存储Bean
     */
    @Bean
    public PersistenceStore<PluginXssConfig, Long> xssConfigStore() {
        return storeFactory.createXssConfigStore();
    }

    /**
     * XSS攻击日志存储Bean
     */
    @Bean
    public PersistenceStore<PluginXssAttackLog, Long> xssAttackLogStore() {
        return storeFactory.createXssAttackLogStore();
    }

    /**
     * 节点日志配置存储Bean
     */
    @Bean
    public PersistenceStore<PluginNodeLoggerConfig, Long> nodeLoggerConfigStore() {
        return storeFactory.createNodeLoggerConfigStore();
    }
}
