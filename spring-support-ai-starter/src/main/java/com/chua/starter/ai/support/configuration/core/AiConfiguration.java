package com.chua.starter.ai.support.configuration.core;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.impl.AIDialogLocalRecordService;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Map;

/**
 * AI 核心自动配置 — 全局 对话客户端 和 Agent 注册体系。
 *
 * <p>Spring Boot 专属层，职责：</p>
 * <ul>
 *   <li>将 {@code utils-*} 核心库的 ChatClient/Agent 桥接到 Spring 容器</li>
 *   <li>通过 SPI 发现所有 ChatClient 实现，聚合为 {@link AggregateChatClient}</li>
 *   <li>创建 {@link ChatClientRegistry} / {@link AgentRegistry} 全局单例</li>
 *   <li>在 {@code ApplicationReadyEvent} 时完成注册中心初始化</li>
 * </ul>
 *
 * <p>核心类型（ChatClient、Agent、AggregateChatClient、AgentMode 等）全部来自
 * {@code utils-support-common-starter}，不在此模块重复定义。</p>
 *
 * <h2>多模型聚合 ChatClient 启动流程</h2>
 * <pre>
 *   1. SPI 发现 ChatClient 实现
 *   2. 聚合为 AggregateChatClient
 *   3. 注册到 ChatClientRegistry 作为默认客户端
 *   4. 业务代码通过 registry.getDefault() 使用
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.ai.support")
@MapperScan("com.chua.starter.ai.support.mapper")
public class AiConfiguration {

    /**
     * 全局 对话客户端 注册中心。
     * @return 对话客户端registry的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClientRegistry chatClientRegistry() {
        log.info("[AiConfiguration] 创建 ChatClientRegistry");
        /**
         * 对话客户端registry。
         * @return 对话客户端registry的结果
         */
        return new ChatClientRegistry();
    }

    /**
     * 全局 Agent 注册中心。
     * @return Agentregistry的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry() {
        log.info("[AiConfiguration] 创建 AgentRegistry");
        /**
         * Agentregistry。
         * @return Agentregistry的结果
         */
        return new AgentRegistry();
    }

    /**
     * 对话记录服务——默认使用内存实现。
     * @return AIdialogrecord服务的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public AIDialogRecordService aiDialogRecordService() {
        log.info("[AiConfiguration] 创建 AIDialogRecordService（内存实现）");
        /**
         * aidialog本地record服务。
         * @return AIdialog本地record服务的结果
         */
        return new AIDialogLocalRecordService();
    }

    /**
     * 默认 对话客户端 — 聚合客户端。
     *
     * <p>通过 SPI 发现 ChatClient 实现，聚合为 AggregateChatClient。
     * 如果找不到任何实现，返回 空（业务层自行处理兜底）。
     * @param applicationContext application上下文
     * @return 默认对话客户端的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient defaultChatClient(ApplicationContext applicationContext) {
        log.info("[AiConfiguration] 发现 ChatClient 实现...");

        List<ChatClient> spiClients = ServiceProvider.of(ChatClient.class).collect();
        log.info("[AiConfiguration] SPI 发现 {} 个 ChatClient", spiClients.size());

        Map<String, ChatClient> springClients = applicationContext.getBeansOfType(ChatClient.class);
        // 排除注册中心自身
        springClients.values().removeIf(c -> c instanceof ChatClientRegistry || c instanceof AgentRegistry);

        log.info("[AiConfiguration] Spring 容器中有 {} 个 ChatClient Bean", springClients.size());

        if (!spiClients.isEmpty() || !springClients.isEmpty()) {
            AggregateChatClientSetting config = new AggregateChatClientSetting();
            config.setStrategy("failover");
            /**
             * aggregate对话客户端。
             * @param config 配置
             * @return aggregate对话客户端的结果
             */
            return new AggregateChatClient(config);
        }

        log.warn("[AiConfiguration] 未发现任何 ChatClient 实现");
        return null;
    }

    /**
     * 容器就绪后初始化注册中心。
     * @param event 事件
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onApplicationReady(ApplicationReadyEvent event) {
        ApplicationContext context = event.getApplicationContext();
        ChatClientRegistry chatClientRegistry = context.getBean(ChatClientRegistry.class);
        AgentRegistry agentRegistry = context.getBean(AgentRegistry.class);

 // 收集所有 对话客户端 Bean
        Map<String, ChatClient> chatClients = context.getBeansOfType(ChatClient.class);
        for (Map.Entry<String, ChatClient> entry : chatClients.entrySet()) {
            ChatClient client = entry.getValue();

            if (client instanceof ChatClientRegistry || client instanceof AgentRegistry) {
                continue;
            }

            if (client instanceof AggregateChatClient aggregate) {
                chatClientRegistry.setAggregate(aggregate);
                chatClientRegistry.setDefault(aggregate);
                chatClientRegistry.register("aggregate", "aggregate", aggregate, "chat", "routing", "aggregate");
            } else {
                chatClientRegistry.register(entry.getKey(), client);
 // 无聚合客户端时，用首个普通客户端兜底默认，保证 resolve对话客户端 永不落空
                if (chatClientRegistry.getDefault() == null) {
                    chatClientRegistry.setDefault(client);
                    log.info("[AiConfiguration] 设置默认 ChatClient: {}", entry.getKey());
                }
            }
        }

        log.info("[AiConfiguration] ChatClientRegistry 初始化完成，共 {} 个客户端", chatClientRegistry.size());

 // 初始化主 Agent
        ChatClient defaultClient = chatClientRegistry.getDefault();
        if (defaultClient != null) {
            try {
                Agent masterAgent = Agent.create("agentscope")
                        .chatClient(defaultClient)
                        .mode(AgentMode.AUTO);
                agentRegistry.setMaster(masterAgent);
                log.info("[AiConfiguration] 主 Agent 已初始化，模式: AUTO");
            } catch (Exception e) {
                log.warn("[AiConfiguration] 主 Agent 初始化失败: {}", e.getMessage());
            }
        }

        log.info("[AiConfiguration] AI 注册体系初始化完成");
    }

    @Bean
    /**
     * AI服务信息contributor
     *
     * @return AI服务信息contributor的结果
     */
    public ServiceInfoContributor aiServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("AI", "AI接口", "/ai/**", "AI接口");
            registry.registerFlag("ai", "AI", true, "AI模块");
        };
    }
}
