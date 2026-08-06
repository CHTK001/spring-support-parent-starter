package com.chua.starter.ai.support.configuration;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RAG 自动装配——引入 AI 模块即提供全局 RAG 能力。
 *
 * <p>全局 RAG 客户端由 {@link RagClientManager} 管理（懒构建 + 配置热更新），
 * 配置读取自 {@code ai_global_setting}。此处仅注册 RAG 配置属性与初始化器。</p>
 *
 * @author CH
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(RagProperties.class)
public class RagAutoConfiguration {

    /**
     * 全局设置初始化器（ApplicationRunner）。
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalSettingInitializer globalSettingInitializer(SysAiGlobalSettingService globalSettingService) {
        return new GlobalSettingInitializer(globalSettingService);
    }
}
