package com.chua.starter.ai.support.configuration.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.openai.support.OpenAiChatClient;
import com.chua.zai.support.ZaiChatClient;
import com.chua.zhipu.support.ZhipuChatClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * 按 YAML 配置构建对话客户端。
 *
 * <p>把「厂商标识 → 实现类」的映射收敛在一处：这些厂商 starter 都已由本模块依赖，
 * 直接 new 即可，不必再借道 SPI 反射（SPI 路径只按类名注册，无法把外部配置传进构造器）。</p>
 *
 * <p>厂商标识取自各实现类 {@code @Spi} 声明的别名，大小写不敏感：</p>
 * <ul>
 *   <li>{@code zhipu} / {@code glm} → {@link ZhipuChatClient}</li>
 *   <li>{@code zai} → {@link ZaiChatClient}</li>
 *   <li>其余（{@code openai} / {@code siliconflow} / {@code sensetime} / {@code github} /
 *       {@code gitee} / {@code dots} 等 OpenAI 兼容网关）→ {@link OpenAiChatClient}</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class AiChatClientFactory {

    /**
     * 工具类禁止实例化。
     */
    private AiChatClientFactory() {
    }

    /**
     * 把 YAML 配置装配为调用客户端。
     *
     * @param properties YAML 配置，不允许为 null
     * @return 对话客户端
     */
    public static ChatClient create(AiChatProperties properties) {
        String provider = properties.provider() == null
                ? ""
                : properties.provider().trim().toLowerCase(Locale.ROOT);

        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(provider)
                .baseUrl(properties.baseUrl())
                .appKey(properties.appKey())
                .appSecret(properties.appSecret())
                .model(properties.model())
                .temperature(properties.temperature())
                .maxTokens(properties.maxTokens())
                .timeoutMillis(properties.timeoutMillis())
                .authScheme(properties.authScheme())
                .authHeader(properties.authHeader())
                .authPrefix(properties.authPrefix())
                .proxy(properties.proxy())
                .build();

        ChatClient client = switch (provider) {
            case "zhipu", "glm" -> new ZhipuChatClient(setting);
            case "zai" -> new ZaiChatClient(setting);
            // 其余按 OpenAI 兼容协议处理；base-url 由配置给出，为空则用实现类内置默认值
            default -> new OpenAiChatClient(setting);
        };
        log.info("[AiChatClientFactory] 已按 YAML 配置装配对话客户端: provider={}, model={}, baseUrl={}",
                provider.isEmpty() ? "(默认 openai)" : provider,
                properties.model(),
                properties.baseUrl() == null ? "(实现类默认)" : properties.baseUrl());
        return client;
    }
}
