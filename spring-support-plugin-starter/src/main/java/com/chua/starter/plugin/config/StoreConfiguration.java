package com.chua.starter.plugin.config;

import com.chua.starter.plugin.entity.PluginSecurityConfig;
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
     * 安全配置存储Bean（统一的限流和黑白名单存储）
     */
    @Bean
    public PersistenceStore<PluginSecurityConfig, Long> securityConfigStore() {
        return storeFactory.createSecurityConfigStore();
    }

    /**
     * XSS配置存储Bean
     */
    @Bean
    public PersistenceStore<PluginXssConfig, Long> xssConfigStore() {
        return storeFactory.createXssConfigStore();
    }

}
