package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.MemoryChatClient;
import com.chua.common.support.ai.runtime.AiRuntimeConfig;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.chat.AiChatProperties;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * {@link AiRuntimeConfig} 的默认实现：把全局设置读取器、生效模型解析器、
 * 客户端注册表与静态配置收敛为一个对外的窄契约。
 *
 * <p>本类是「适配器」，不持有状态、不做缓存：每次调用都取当前真实值，
 * 因此 AI 控制台改配置后业务侧立即读到新值。所有依赖都以 {@code null} 容错，
 * 便于在未启用 AI 的部署里照常装配。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class DefaultAiRuntimeConfig implements AiRuntimeConfig {

    /**
     * 全局设置读取器（读 sys_ai_global_setting）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 生效对话模型解析器（runtime > 兼容旧键 > 默认）
     */
    private final EffectiveChatModelResolver modelResolver;

    /**
     * 对话客户端注册表
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * YAML 静态对话配置
     */
    private final AiChatProperties chatProperties;

    /**
     * 构造运行时配置适配器。
     *
     * @param settingReader      全局设置读取器，可空
     * @param modelResolver      生效模型解析器，可空
     * @param chatClientRegistry 客户端注册表，可空
     * @param chatProperties     静态对话配置，可空
     */
    public DefaultAiRuntimeConfig(GlobalSettingReader settingReader,
                                  EffectiveChatModelResolver modelResolver,
                                  ChatClientRegistry chatClientRegistry,
                                  AiChatProperties chatProperties) {
        this.settingReader = settingReader;
        this.modelResolver = modelResolver;
        this.chatClientRegistry = chatClientRegistry;
        this.chatProperties = chatProperties;
    }

    @Override
    public String runtimeSetting(String group, String field) {
        if (settingReader == null) {
            return null;
        }
        try {
            return settingReader.getStrDirect(group, field);
        } catch (Exception e) {
            log.debug("[AiRuntimeConfig] 读取配置失败 group={} field={}: {}", group, field, e.getMessage());
            return null;
        }
    }

    @Override
    public String effectiveChatModel(String explicitModel) {
        if (modelResolver == null) {
            return null;
        }
        try {
            return modelResolver.resolve(explicitModel);
        } catch (Exception e) {
            log.debug("[AiRuntimeConfig] 解析生效模型失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ChatClient chatClient(String provider) {
        if (chatClientRegistry == null || StringUtils.isBlank(provider)) {
            return null;
        }
        try {
            return chatClientRegistry.getByProvider(provider);
        } catch (Exception e) {
            log.debug("[AiRuntimeConfig] 取厂商客户端失败 provider={}: {}", provider, e.getMessage());
            return null;
        }
    }

    @Override
    public ChatClient defaultChatClient() {
        if (chatClientRegistry == null) {
            return null;
        }
        ChatClient client;
        try {
            client = chatClientRegistry.getDefault();
        } catch (Exception e) {
            log.debug("[AiRuntimeConfig] 取默认客户端失败: {}", e.getMessage());
            return null;
        }
        // MemoryChatClient 是未装配真实客户端时的空壳，调用必失败，不视为可用默认
        return client instanceof MemoryChatClient ? null : client;
    }

    @Override
    public Set<String> chatProviders() {
        if (chatClientRegistry == null) {
            return Collections.emptySet();
        }
        try {
            Set<String> providers = chatClientRegistry.getProviders();
            return providers == null ? Collections.emptySet() : providers;
        } catch (Exception e) {
            log.debug("[AiRuntimeConfig] 取厂商集合失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    @Override
    public boolean staticChatEnabled() {
        return chatProperties != null && chatProperties.enabled();
    }

    @Override
    public String staticChatProvider() {
        return chatProperties == null ? null : chatProperties.provider();
    }

    @Override
    public String staticChatModel() {
        return chatProperties == null ? null : chatProperties.model();
    }

    @Override
    public boolean staticChatKeyMissing() {
        return chatProperties != null
                && chatProperties.enabled()
                && !StringUtils.hasText(chatProperties.appKey());
    }
}
