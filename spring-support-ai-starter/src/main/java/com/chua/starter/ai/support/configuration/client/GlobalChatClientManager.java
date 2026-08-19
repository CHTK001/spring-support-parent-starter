package com.chua.starter.ai.support.configuration.client;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.chat.ChatClientSettings;
import com.chua.starter.ai.support.chat.ChatClientSettingsResolver;
import com.chua.starter.ai.support.chat.DefaultScopeChatClient;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 全局 ChatClient 管理器 —— 基于全局配置动态构建全局 ChatClient。
 *
 * <p>引入 AI 模块即提供全局 ChatClient，配置组装自两条链路：</p>
 * <ul>
 *   <li>{@code sys_ai_global_setting}（runtime 分组）：provider/apiKey/baseUrl/factory 等主链路配置，由
 *       {@link ChatClientSettingsResolver} 解析</li>
 *   <li>{@code ai_global_setting}（单行 id=1）：defaultModel、systemPrompt、temperature、maxTokens、timeout、
 *       chatClientEnabled 等面板配置</li>
 * </ul>
 *
 * <p>配置通过全局配置面板修改后调用 {@link #rebuild()} 热更新：</p>
 * <ol>
 *   <li>通过 {@link DefaultListableBeanFactory} 以 {@code globalChatClient} 单例名动态替换 Spring Bean</li>
 *   <li>更新 {@link ChatClientRegistry} 的默认客户端与模型索引</li>
 *   <li>基于新客户端重建主 Agent 并同步到 {@link AgentRegistry}</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalChatClientManager {

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * 旧版 KV 全局配置服务（runtime 主链路配置）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "global";

    /**
     * 全局 ChatClient 在 Spring 容器中的 Bean 名称
     */
    private static final String GLOBAL_CLIENT_BEAN_NAME = "globalChatClient";

    /**
     * 主 Agent 在 Spring 容器中的 Bean 名称
     */
    private static final String MASTER_AGENT_BEAN_NAME = "masterAgent";

    /**
     * ChatClient 注册中心
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * Agent 注册中心
     */
    private final AgentRegistry agentRegistry;

    /**
     * Spring 单例 Bean 工厂，用于动态替换全局 ChatClient Bean
     */
    private final DefaultListableBeanFactory beanFactory;

    /**
     * 当前生效的全局 ChatClient（懒构建，热更新时替换）
     */
    private volatile ChatClient chatClient;

    /**
     * 当前生效的全局 ChatClient 设置（用于日志与模型索引）
     */
    private volatile ChatClientSettings settings;

    /**
     * 获取当前全局 ChatClient，未构建时按全局配置懒构建。
     *
     * @return 全局 ChatClient（配置缺失时可能为 null）
     */
    public ChatClient current() {
        ChatClient client = chatClient;
        if (client == null) {
            synchronized (this) {
                if (chatClient == null) {
                    rebuild();
                }
                client = chatClient;
            }
        }
        return client;
    }

    /**
     * 获取当前生效的全局 ChatClient 设置。
     *
     * @return 全局 ChatClient 设置（未初始化时可能为 null）
     */
    public ChatClientSettings currentSettings() {
        return settings;
    }

    /**
     * 按最新全局配置重建全局 ChatClient（热更新）。
     *
     * <p>重建成功后依次：BeanFactory 替换 {@code globalChatClient} 单例 Bean、
     * 更新 ChatClientRegistry 默认客户端与模型索引、重建主 Agent。</p>
     */
    public synchronized void rebuild() {
        ChatClientSettings nextSettings = buildSettings();
        if (nextSettings == null) {
            log.warn("[GlobalChatClient] 全局 ChatClient 构建失败，保持当前实例");
            return;
        }
        ChatClient next = new DefaultScopeChatClient(nextSettings);
        ChatClient old = chatClient;
        this.chatClient = next;
        this.settings = nextSettings;
        replaceBean(next);
        registryDefault(next, nextSettings);
        rebuildMasterAgent(next);
        if (old != null && old != next) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }
        log.info("[GlobalChatClient] 全局 ChatClient 已热更新: model={}, factory={}, provider={}",
                nextSettings.getDefaultModel(), nextSettings.getFactory(), nextSettings.getProvider());
    }

    /**
     * 容器就绪后按全局配置初始化全局 ChatClient 与主 Agent。
     *
     * @param event 容器就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady(ApplicationReadyEvent event) {
        rebuild();
    }

    /**
     * 基于全局配置构建全局 ChatClient 设置。
     *
     * <p>优先从 {@code sys_ai_global_setting}（runtime 分组）解析 provider/apiKey/baseUrl 等主链路配置，
     * 再用 {@code global} 分组面板字段覆盖默认模型、系统提示词、温度等参数。</p>
     *
     * @return ChatClient 设置；chatClientEnabled=false 或解析失败时返回 null
     */
    private ChatClientSettings buildSettings() {
        boolean enabled = settingReader.getBool(GROUP, "chat_client_enabled", true);
        com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry.getInstance().registerFlag(
                "chat-client", "AI", enabled, "全局 ChatClient");
        if (!enabled) {
            log.warn("[GlobalChatClient] chatClientEnabled=false，全局 ChatClient 已禁用");
            return null;
        }
        ChatClientSettings settings;
        try {
            settings = ChatClientSettingsResolver.resolve(sysAiGlobalSettingService);
        } catch (Exception e) {
            log.warn("[GlobalChatClient] 解析主链路配置失败，全局 ChatClient 暂不可用: {}", e.getMessage());
            return null;
        }
        applyGlobalSetting(settings);
        log.info("[GlobalChatClient] 构建全局 ChatClient: model={}, factory={}, provider={}, systemPrompt={}",
                settings.getDefaultModel(), settings.getFactory(), settings.getProvider(),
                StringUtils.isBlank(settings.getSystemPrompt()) ? "-" : "configured");
        return settings;
    }

    /**
     * 用 {@code sys_ai_global_setting} global 分组面板字段覆盖默认模型、系统提示词、温度、最大 tokens、超时。
     *
     * @param settings 待覆盖的设置
     */
    private void applyGlobalSetting(ChatClientSettings settings) {
        String defaultModel = settingReader.getStr(GROUP, "default_model");
        if (StringUtils.hasText(defaultModel)) {
            settings.setDefaultModel(defaultModel);
        }
        String systemPrompt = settingReader.getStr(GROUP, "system_prompt");
        if (StringUtils.hasText(systemPrompt)) {
            settings.setSystemPrompt(systemPrompt);
        }
        var temperature = settingReader.getDecimalOrNull(GROUP, "temperature");
        if (temperature != null) {
            settings.setTemperature(temperature.doubleValue());
        }
        Integer maxTokens = settingReader.getIntOrNull(GROUP, "max_tokens");
        if (maxTokens != null) {
            settings.setMaxTokens(maxTokens);
        }
        Integer timeout = settingReader.getIntOrNull(GROUP, "timeout");
        if (timeout != null && timeout > 0) {
            settings.setTimeoutMillis(timeout * 1000L);
        }
    }

    /**
     * 通过 DefaultListableBeanFactory 动态替换 {@code globalChatClient} 单例 Bean。
     *
     * @param client 新的全局 ChatClient
     */
    private void replaceBean(ChatClient client) {
        try {
            if (beanFactory.containsSingleton(GLOBAL_CLIENT_BEAN_NAME)) {
                beanFactory.destroySingleton(GLOBAL_CLIENT_BEAN_NAME);
            }
            beanFactory.registerSingleton(GLOBAL_CLIENT_BEAN_NAME, client);
            log.debug("[GlobalChatClient] 已通过 BeanFactory 注册单例 Bean: {}", GLOBAL_CLIENT_BEAN_NAME);
        } catch (Exception e) {
            log.warn("[GlobalChatClient] BeanFactory 替换全局 ChatClient Bean 失败: {}", e.getMessage());
        }
    }

    /**
     * 更新 ChatClientRegistry 默认客户端，并按模型名注册索引。
     *
     * @param client   新的全局 ChatClient
     * @param settings 全局 ChatClient 设置
     */
    private void registryDefault(ChatClient client, ChatClientSettings settings) {
        chatClientRegistry.setDefault(client);
        String model = settings != null ? settings.getDefaultModel() : null;
        if (StringUtils.hasText(model)) {
            chatClientRegistry.register("global", model, client, "chat", "routing");
        }
    }

    /**
     * 基于新的全局 ChatClient 重建主 Agent，并动态注册 {@code masterAgent} Bean。
     *
     * @param client 新的全局 ChatClient
     */
    private void rebuildMasterAgent(ChatClient client) {
        try {
            Agent masterAgent = Agent.create("agentscope")
                    .chatClient(client)
                    .mode(AgentMode.AUTO);
            agentRegistry.setMaster(masterAgent);
            replaceAgentBean(masterAgent);
            log.info("[GlobalChatClient] 主 Agent 已基于新全局 ChatClient 重建");
        } catch (Exception e) {
            log.warn("[GlobalChatClient] 主 Agent 重建失败: {}", e.getMessage());
        }
    }

    /**
     * 通过 DefaultListableBeanFactory 动态替换 {@code masterAgent} 单例 Bean。
     *
     * @param agent 新的主 Agent
     */
    private void replaceAgentBean(Agent agent) {
        try {
            if (beanFactory.containsSingleton(MASTER_AGENT_BEAN_NAME)) {
                beanFactory.destroySingleton(MASTER_AGENT_BEAN_NAME);
            }
            beanFactory.registerSingleton(MASTER_AGENT_BEAN_NAME, agent);
            log.debug("[GlobalChatClient] 已通过 BeanFactory 注册单例 Bean: {}", MASTER_AGENT_BEAN_NAME);
        } catch (Exception e) {
            log.warn("[GlobalChatClient] BeanFactory 替换主 Agent Bean 失败: {}", e.getMessage());
        }
    }
}
