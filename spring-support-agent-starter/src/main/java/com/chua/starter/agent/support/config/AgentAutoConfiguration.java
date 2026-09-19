package com.chua.starter.agent.support.config;

import com.chua.starter.agent.support.service.AgentAttachService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Agent 自动注入自动配置 — 引入 {@code spring-support-agent-starter} 依赖即生效。
 *
 * <p>注册 {@link AgentAttachService}（实现 {@code SmartInitializingSingleton}，单例就绪后触发），
 * 宿主应用无需任何启动参数或代码，启动后自动完成 Runtime Agent 自注入。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentAttachProperties.class)
@ConditionalOnProperty(prefix = "runtime.agent", name = "enabled",
        havingValue = "true", matchIfMissing = true)
/**
 * AgentAuto配置类，提供相关能力。
 *
 * @author CH
 * @since 1.0.0
 */
public class AgentAutoConfiguration {

    /**
     * 注册 Agent 自注入服务（宿主可自定义同类型 Bean 覆盖）。
     *
     * @param properties 配置项
     * @return 自注入服务
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentAttachService agentAttachService(AgentAttachProperties properties) {
        return new AgentAttachService(properties);
    }
}
