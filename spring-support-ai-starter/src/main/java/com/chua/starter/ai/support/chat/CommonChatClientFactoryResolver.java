package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;

/**
 * common ChatClient SPI 解析器。
 *
 * @author CH
 * @since 2026/04/03
 */
final class CommonChatClientFactoryResolver {

    private CommonChatClientFactoryResolver() {
    }

    /**
     * 按工厂名称创建 common ChatClient。
     *
     * @param factoryName 工厂名称
     * @param setting     大模型设置
     * @return common ChatClient
     */
    static ChatClient create(String factoryName, ChatClientSetting setting) {
        com.chua.common.support.ai.chat.ChatClient raw = createRaw(factoryName, setting);
        return (ChatClient) raw;
    }

    private static com.chua.common.support.ai.chat.ChatClient createRaw(String factoryName, ChatClientSetting setting) {
        ChatClientSetting chatClientSetting = ChatClientSetting.builder()
                .provider(factoryName)
                .appKey(setting != null ? setting.getAppKey() : null)
                .baseUrl(setting != null ? setting.getBaseUrl() : null)
                .build();
        // 始终携带 setting 创建，保证 ZhipuChatClient 等无无参构造的实现能注入 apiKey/baseUrl
        try {
            com.chua.common.support.ai.chat.ChatClient client = ChatClient.create(chatClientSetting);
            if (client != null) {
                return client;
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CommonChatClientFactoryResolver.class)
                    .warn("[CommonChatClientFactoryResolver] 创建 ChatClient[provider={}] 失败: {}",
                            factoryName, e.getMessage());
        }
        if (StringUtils.isBlank(factoryName)) {
            return null;
        }
        return ChatClient.create(ChatClientSetting.builder()
                .provider(factoryName)
                .appKey(setting != null ? setting.getAppKey() : null)
                .baseUrl(setting != null ? setting.getBaseUrl() : null)
                .build());
    }
}