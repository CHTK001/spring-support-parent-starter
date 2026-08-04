package com.chua.starter.ai.support.configuration;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 对话客户端自动配置
 *
 * <p>读取 {@code spring.ai.chat.*} 配置，创建 OpenAI 兼容协议的 {@link ChatClient}
 * 并注册到 {@link ChatClientRegistry}，同时把配置声明的模型定义（含能力标签）
 * 写入注册中心，供 {@code /v1/models} 与前端能力开关使用。</p>
 *
 * <p>仅当配置了 {@code spring.ai.chat.api-key} 时创建真实客户端；未配置时仅注册
 * 模型定义（前端仍可正常显示模型列表与能力开关）。</p>
 *
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(AiChatProperties.class)
public class AiChatClientConfiguration {

    private final AiChatProperties properties;
    private final ChatClientRegistry chatClientRegistry;

    /**
     * 容器就绪后按配置注册 ChatClient 与模型定义。
     */
    @PostConstruct
    public void registerClients() {
        // 1. 注册模型定义（含能力标签）
        List<AiChatProperties.ModelItem> models = properties.getModels();
        if (models != null) {
            for (AiChatProperties.ModelItem item : models) {
                if (item == null || !StringUtils.hasText(item.getId())) {
                    continue;
                }
                ModelDefinition def = ModelDefinition.builder()
                        .id(item.getId())
                        .name(StringUtils.hasText(item.getName()) ? item.getName() : item.getId())
                        .description(item.getDescription())
                        .capabilities(item.getCapabilities())
                        .build();
                chatClientRegistry.registerModel(def);
            }
            log.info("[AiChatClientConfiguration] 已注册 {} 个模型定义", models.size());
        }

        // 2. 创建真实 ChatClient（仅当配置了 api-key）
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.warn("[AiChatClientConfiguration] 未配置 spring.ai.chat.api-key，跳过 ChatClient 创建（模型列表仍可用）");
            return;
        }

        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(properties.getProvider())
                .appKey(apiKey)
                .baseUrl(properties.getBaseUrl())
                .build();
        ChatClient client = ChatClient.create(setting);

        // 注册到注册中心：provider 索引 + 各模型索引
        String provider = properties.getProvider();
        chatClientRegistry.register(provider, client);
        if (models != null) {
            for (AiChatProperties.ModelItem item : models) {
                if (item != null && StringUtils.hasText(item.getId())) {
                    chatClientRegistry.register(provider, item.getId(), client,
                            item.getCapabilities() == null
                                    ? new String[]{"chat"}
                                    : item.getCapabilities().toArray(new String[0]));
                }
            }
        }
        log.info("[AiChatClientConfiguration] 已注册 ChatClient: provider={}, baseUrl={}, models={}",
                provider, properties.getBaseUrl(), models == null ? 0 : models.size());
    }
}
