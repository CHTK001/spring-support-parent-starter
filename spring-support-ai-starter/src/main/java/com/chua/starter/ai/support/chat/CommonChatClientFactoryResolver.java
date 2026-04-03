package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.bigmodel.BigModelSetting;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.utils.StringUtils;

/**
 * common ChatClient.Factory SPI 解析器。
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
    static com.chua.common.support.ai.ChatClient create(String factoryName, BigModelSetting setting) {
        ServiceProvider<com.chua.common.support.ai.ChatClient.Factory> provider =
                ServiceProvider.of(com.chua.common.support.ai.ChatClient.Factory.class);
        com.chua.common.support.ai.ChatClient.Factory factory = StringUtils.isBlank(factoryName)
                ? provider.getDefault()
                : provider.getNewExtension(factoryName);
        if (factory == null) {
            factory = provider.getDefault();
        }
        if (factory == null) {
            throw new IllegalStateException("No common ChatClient.Factory available");
        }
        return factory.create(setting);
    }
}
