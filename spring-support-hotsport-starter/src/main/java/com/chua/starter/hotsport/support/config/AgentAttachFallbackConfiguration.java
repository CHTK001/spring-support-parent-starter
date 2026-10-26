package com.chua.starter.hotsport.support.config;

import com.chua.starter.agent.support.config.AgentAttachProperties;
import com.chua.starter.agent.support.service.AgentAttachService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Agent 自注入降级配置 — 独立部署形态下关掉「观测自己」。
 *
 * <p><b>为什么需要</b>：hotsport 是<a href="https://example.com/独立观测后台">观测后台</a>，
 * 默认由 {@code spring-support-agent-starter} 的 {@code AgentAutoConfiguration} 在启动时向
 * <b>当前 JVM</b> 自注入 Runtime Agent（{@link AgentAttachService} 实现
 * {@code SmartInitializingSingleton}）。在 JDK 25 上，agent 注入后的
 * {@code retransformLoadedClasses} 对已加载类（Tomcat / Spring / JDK）做字节码重转换时
 * 会让 JVM 直接退出（exit=1、无异常、无 hs_err），观测组件却反手把被测宿主拖死。</p>
 *
 * <p><b>降级策略</b>：当用户显式配置 {@code plugin.agent.enabled=false} 时，
 * {@code AgentAutoConfiguration} 整体不装配（该自动配置自带
 * {@code @ConditionalOnProperty(name="enabled", havingValue="true", matchIfMissing=true)}），
 * 而 {@code ApmQueryService} / {@code ApmController} 又强依赖 {@link AgentAttachService}
 * Bean —— 缺了它 Spring 上下文直接启动失败。本配置补一个「禁用态」实例：
 * <ul>
 *   <li>{@link #disabledAgentAttachService()} 在 agent 自动配置缺席（enabled=false）时生效；</li>
 *   <li>复用真实 {@link AgentAttachService}，只是 {@code properties.isEnabled()=false}，
 *       {@code attach()} 幂等跳过注入、{@code isAgentActive()} 恒 false、其余状态查询照常；</li>
 *   <li>本地 APM 引擎（{@code ApmQueryService.startLocalApmEngine}，8 个 handler，与查询同源）
 *       不受影响，数据照常采集。观测不观测自己，符合观测组件不阻断宿主的原则。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentAttachProperties.class)
public class AgentAttachFallbackConfiguration {

    /**
     * 禁用态自注入服务 —— 仅当 agent 自动配置未装配（{@code plugin.agent.enabled=false}）时兜底。
     *
     * @return 禁用态注入服务实例
     */
    @Bean
    @ConditionalOnMissingBean(AgentAttachService.class)
    public AgentAttachService disabledAgentAttachService(AgentAttachProperties properties) {
        // 显式关闭注入（幂等保护在 attach() 内；即便 properties 被外部改回 true，
        // 也只在调用方显式触发时才注入，不自动启动）
        properties.setEnabled(false);
        return new AgentAttachService(properties);
    }
}