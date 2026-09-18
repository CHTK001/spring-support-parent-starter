package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;

/**
* 通用 对话客户端 SPI 解析器。
*
* @since 2026/04/03
* @author CH
 */
final class CommonChatClientFactoryResolver {

    /** 创建 通用对话客户端工厂解析器 实例 */
    private CommonChatClientFactoryResolver() {
    }

    /**
    * 按工厂名称创建 通用 对话客户端。
    *
    * <p>provider 优先取 {@code setting.provider}（真实厂商标识），
    * 缺失时回退 {@code factoryName}。避免 工厂 与 提供者 混用
    * 导致 SPI 无法找到对应 对话客户端（如 工厂=默认 但 提供者=openai）。</p>
    *
    * @param factoryName 工厂名称
    * @param setting     大模型设置
    * @return common 对话客户端
    */
    static ChatClient create(String factoryName, ChatClientSetting setting) {
        com.chua.common.support.ai.chat.ChatClient raw = createRaw(factoryName, setting);
        return (ChatClient) raw;
    }

    /**
    * 创建Raw
    *
    * @param factoryName 工厂名称
    * @param setting setting
    * @return 创建raw的结果
    */
    private static com.chua.common.support.ai.chat.ChatClient createRaw(String factoryName, ChatClientSetting setting) {
        String provider = setting != null && StringUtils.isNotBlank(setting.getProvider())
                ? setting.getProvider()
                : factoryName;
        ChatClientSetting chatClientSetting = ChatClientSetting.builder()
                .provider(provider)
                .appKey(setting != null ? setting.getAppKey() : null)
                .baseUrl(setting != null ? setting.getBaseUrl() : null)
                .extraHeaders(setting != null ? setting.getExtraHeaders() : null)
                .proxy(setting != null ? setting.getProxy() : null)
                .build();
 // 始终携带 setting 创建，保证 zhipu对话客户端 等无无参构造的实现能注入 api钥匙/baseurl
        try {
            com.chua.common.support.ai.chat.ChatClient client = ChatClient.create(chatClientSetting);
            if (client != null) {
                return client;
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CommonChatClientFactoryResolver.class)
                    .warn("[CommonChatClientFactoryResolver] 创建 ChatClient[provider={}] 失败: {}",
                            provider, e.getMessage());
        }
        if (StringUtils.isBlank(provider)) {
            return null;
        }
        return ChatClient.create(ChatClientSetting.builder()
                .provider(provider)
                .appKey(setting != null ? setting.getAppKey() : null)
                .baseUrl(setting != null ? setting.getBaseUrl() : null)
                .extraHeaders(setting != null ? setting.getExtraHeaders() : null)
                .proxy(setting != null ? setting.getProxy() : null)
                .build());
    }
}
