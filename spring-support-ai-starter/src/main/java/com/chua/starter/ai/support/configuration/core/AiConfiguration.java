package com.chua.starter.ai.support.configuration.core;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.MemoryChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.chat.AiChatClientFactory;
import com.chua.starter.ai.support.configuration.chat.AiChatProperties;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.impl.AIDialogLocalRecordService;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

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
@EnableConfigurationProperties(AiChatProperties.class)
public class AiConfiguration {

    /**
     * YAML 配置的对话客户端参数。
     */
    private final AiChatProperties aiChatProperties;

    /**
     * 构造方法，创建 AiConfiguration 实例。
     *
     * @param aiChatProperties YAML 配置，不允许为 null
     */
    public AiConfiguration(AiChatProperties aiChatProperties) {
        this.aiChatProperties = aiChatProperties;
    }

    /**
     * YAML 配置的对话客户端。
     *
     * <p>声明在 {@link #defaultChatClient(ApplicationContext)} 之前：后者带
     * {@code @ConditionalOnMissingBean(ChatClient)}，本 bean 先注册后它就会被跳过，
     * 从而不会出现「聚合客户端」和「YAML 客户端」两个 ChatClient bean 相互干扰。
     * 注册中心在 {@code ApplicationReadyEvent} 时按类型收集 ChatClient，本 bean 会被收进去。</p>
     *
     * <p>只在 {@code chua.ai.chat.enabled=true} 时创建；没配密钥的环境不会凭空多出一个
     * 必然调用失败的客户端。</p>
     *
     * <p>{@code app-key} 为空时同样不创建 —— 否则能力门会报「可用」而调用必然 401，
     * 前端会显示成可用状态、点下去才失败。缺密钥由 {@code onApplicationReady} 打 WARN 说明。</p>
     *
     * @param properties YAML 配置
     * @return 对话客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "chua.ai.chat", name = "enabled", havingValue = "true")
    @ConditionalOnExpression("'${chua.ai.chat.app-key:}'.trim().length() > 0")
    public ChatClient yamlChatClient(AiChatProperties properties) {
        return AiChatClientFactory.create(properties);
    }

    /**
     * YAML 对话客户端的 Bean 名。
     *
     * <p>容器就绪后 {@link #onApplicationReady} 收集客户端时按 Bean 名登记，
     * 该常量用于识别「这就是 YAML 装配的那个客户端」，以便再按其 provider 补登记一次。</p>
     */
    private static final String YAML_CHAT_CLIENT_BEAN = "yamlChatClient";

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

        // collect() 返回不可变集合，removeIf 会抛 UnsupportedOperationException，必须先拷成可变列表
        List<ChatClient> spiClients = new java.util.ArrayList<>(ServiceProvider.of(ChatClient.class).collect());
        // 剔除演示用的内存客户端：它永远「成功」返回固定文案，一旦进入 failover 聚合且排在
        // 前面（SPI 收集顺序），真实供应商永远不会被调用，RAG 会静默产出「内存模式」假答案。
        // 它不是真实能力，任何生产装配都不应带上它。
        spiClients.removeIf(c -> c instanceof MemoryChatClient);
        log.info("[AiConfiguration] SPI 发现 {} 个 ChatClient（已剔除内存演示客户端）", spiClients.size());

        Map<String, ChatClient> springClients = applicationContext.getBeansOfType(ChatClient.class);
        // 排除注册中心自身
        springClients.values().removeIf(c -> c instanceof ChatClientRegistry || c instanceof AgentRegistry);

        log.info("[AiConfiguration] Spring 容器中有 {} 个 ChatClient Bean", springClients.size());

        if (!spiClients.isEmpty() || !springClients.isEmpty()) {
            AggregateChatClientSetting config = new AggregateChatClientSetting();
            config.setStrategy("failover");
            AggregateChatClient aggregate = new AggregateChatClient(config);
            // 必须把发现到的客户端**真正装载进聚合路由**，否则聚合是个空壳：
            // AggregateChatClient 只持有 allClients，配置里的 clients 是「provider/apiKey」
            // 这类描述性 DTO，装不下已经是实例的 ChatClient，只能用 addClient 注入。
            // 少了这段的后果不是启动失败，而是运行期每次调用都抛
            //   IllegalArgumentException: No clients configured
            // 并被 RagClientManager 包成
            //   AggregateChatClient failed: No clients configured
            // 表现为 /ask 等所有对话接口 100% 返回 500「Node execution failed: generate」，
            // 启动日志一切正常（因为判定条件只检查集合非空），非常难定位。
            List<ChatClient> loaded = new java.util.ArrayList<>(spiClients);
            int index = 0;
            for (ChatClient client : spiClients) {
                aggregate.addClient("spi-" + index++, null, 1, client);
            }
            for (Map.Entry<String, ChatClient> entry : springClients.entrySet()) {
                // 同一个实例可能既被 SPI 发现又被 Spring 收录，按身份去重避免重复装载
                if (loaded.contains(entry.getValue())) {
                    continue;
                }
                loaded.add(entry.getValue());
                aggregate.addClient(entry.getKey(), null, 1, entry.getValue());
            }
            log.info("[AiConfiguration] 聚合客户端装载完成: spi={} spring={} 聚合内实际={}",
                    spiClients.size(), springClients.size(), aggregate.clientCount());
            if (aggregate.clientCount() == 0) {
                // 走到这里说明发现到的客户端都装载失败，宁可返回 null 让上层明确报
                // 「未配置可用 AI 客户端」，也不要交出一个必然抛 No clients configured 的空壳
                log.error("[AiConfiguration] 聚合客户端为空，对话能力不可用");
                return null;
            }
            return aggregate;
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

        // 开了 chua.ai.chat.enabled 却没配密钥时，bean 会被下面的条件挡掉（不创建调用必失败的客户端），
        // 这里补一条可操作的告警 —— 否则现象只是「AI 不可用」，看不出是漏配了密钥。
        if (aiChatProperties.enabled() && !StringUtils.hasText(aiChatProperties.appKey())) {
            log.warn("[AiConfiguration] chua.ai.chat.enabled=true 但未配置密钥，AI 对话客户端不会创建；"
                    + "请设置 chua.ai.chat.app-key 或环境变量 AI_CHAT_APP_KEY（provider={}）",
                    aiChatProperties.provider());
        }

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
                // YAML 装配的客户端再按「配置里的 provider」补登记一次。
                // 否则它只挂在 Bean 名（yamlChatClient）下，调用方按 provider 取客户端
                // （如 ChatClientRegistry#getByProvider("agnes")）永远取不到，
                // 「按 provider 指定模型」就成了一个设了不报错、也不生效的开关。
                String configuredProvider = aiChatProperties.provider();
                if (YAML_CHAT_CLIENT_BEAN.equals(entry.getKey())
                        && StringUtils.hasText(configuredProvider)
                        && !configuredProvider.trim().equalsIgnoreCase(entry.getKey())) {
                    chatClientRegistry.register(configuredProvider.trim(), client);
                    log.info("[AiConfiguration] 额外按 provider 登记 YAML 对话客户端: provider={}",
                            configuredProvider.trim());
                }
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
