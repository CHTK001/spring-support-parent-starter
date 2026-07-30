package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.spi.ServiceProvider;
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
        ServiceProvider<ChatClient> provider =
                ServiceProvider.of(ChatClient.class);
        ChatClient client = StringUtils.isBlank(factoryName)
                ? provider.getDefault()
                : provider.getNewExtension(factoryName);
        if (client == null) {
            client = provider.getDefault();
        }
        if (client == null) {
            ChatClientSetting chatClientSetting = ChatClientSetting.builder()
                    .provider(factoryName)
                    .appKey(setting != null ? setting.getAppKey() : null)
                    .baseUrl(setting != null ? setting.getBaseUrl() : null)
                    .build();
            return ChatClient.create(chatClientSetting);
        }
        return client;
    }
}