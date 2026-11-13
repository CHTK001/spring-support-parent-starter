package com.chua.starter.ai.support.configuration.actuator;

import com.chua.starter.ai.support.configuration.client.GlobalSettingDefinitions;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.properties.DefaultModelResolver;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * AI Actuator 只读端点自动配置。
 *
 * <p>只有宿主 classpath 里存在 Actuator 时才装配；未引入 Actuator 的宿主不会因为
 * 本 starter 多出任何类加载依赖（依赖在 pom 中为 optional）。</p>
 *
 * <p>端点本身只读、不改配置；凭据字段在 {@link AiConfigEndpoint} 中统一脱敏。
 * 暴露哪些端点由宿主的 {@code management.endpoints.web.exposure.include} 决定；
 * 示例应用（ai-starter.yml）默认暴露 {@code ai-config / ai-models / ai-beans / ai-cache}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnProperty(value = "chua.ai.actuator.enabled", havingValue = "true", matchIfMissing = true)
public class AiActuatorAutoConfiguration {

    /**
     * 生效配置端点。
     *
     * @param definitions    分组定义
     * @param settingService 全局设置服务
     * @param environment    环境（YAML 覆盖判断）
     * @return 端点
     */
    @Bean
    @ConditionalOnMissingBean
    public AiConfigEndpoint aiConfigEndpoint(GlobalSettingDefinitions definitions,
                                             SysAiGlobalSettingService settingService,
                                             Environment environment) {
        return new AiConfigEndpoint(definitions, settingService, environment);
    }

    /**
     * 默认模型端点。
     *
     * @param defaultModelResolver 默认模型解析器
     * @param settingReader        配置读取（runtime 分组）
     * @return 端点
     */
    @Bean
    @ConditionalOnMissingBean
    public AiModelsEndpoint aiModelsEndpoint(DefaultModelResolver defaultModelResolver,
                                             GlobalSettingReader settingReader) {
        return new AiModelsEndpoint(defaultModelResolver, settingReader);
    }

    /**
     * 客户端 Bean 端点。
     *
     * @param chatClientRegistry  对话客户端注册中心
     * @param agentProviderFactory Agent 引擎工厂
     * @param settingReader       配置读取
     * @return 端点
     */
    @Bean
    @ConditionalOnMissingBean
    public AiBeansEndpoint aiBeansEndpoint(ChatClientRegistry chatClientRegistry,
                                           AgentProviderFactory agentProviderFactory,
                                           GlobalSettingReader settingReader) {
        return new AiBeansEndpoint(chatClientRegistry, agentProviderFactory, settingReader);
    }

    /**
     * 缓存端点。
     *
     * @param applicationContext 上下文
     * @return 端点
     */
    @Bean
    @ConditionalOnMissingBean
    public AiCacheEndpoint aiCacheEndpoint(ApplicationContext applicationContext) {
        return new AiCacheEndpoint(applicationContext);
    }
}
