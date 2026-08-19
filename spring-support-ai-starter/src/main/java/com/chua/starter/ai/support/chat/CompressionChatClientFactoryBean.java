package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.springframework.beans.factory.FactoryBean;

/**
 * 压缩专用 ChatClient FactoryBean，支持运行时重建。
 * <p>
 * 读取 sys_ai_global_setting(runtime) 中的 compression_model 和压缩专用 provider 配置，
 * 如果 compression_model 为空则不创建压缩专用 ChatClient（回落到主 ChatClient）。
 *
 * @since 2026/05/26
 * @author CH
 */
public class CompressionChatClientFactoryBean implements FactoryBean<ChatClient> {

    /**
     * 配置分组
     */
    private static final String GROUP = "runtime";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 当前生效的压缩 ChatClient（可能为 null，回落到主 ChatClient）
     */
    private volatile ChatClient chatClient;

    /**
     * 构造压缩 ChatClient FactoryBean，启动即尝试构建。
     *
     * @param sysAiGlobalSettingService 全局设置服务
     */
    public CompressionChatClientFactoryBean(SysAiGlobalSettingService sysAiGlobalSettingService) {
        this.sysAiGlobalSettingService = sysAiGlobalSettingService;
        this.chatClient = buildClientSafe();
    }

    /** Rebuild */
    public synchronized void rebuild() {
        ChatClient old = this.chatClient;
        ChatClient newClient = buildClientSafe();
        if (newClient != null) {
            this.chatClient = newClient;
            closeQuietly(old);
        }
    }

    /** 构建ClientSafe */
    private ChatClient buildClientSafe() {
        try {
            return buildClient();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CompressionChatClientFactoryBean.class)
                    .warn("[CompressionChatClient] 构建失败，压缩将回落到主 ChatClient: {}", e.getMessage());
            return null;
        }
    }

    /** 构建Client */
    private ChatClient buildClient() {
        String compressionModel = getValue("compression_model");
        if (StringUtils.isBlank(compressionModel)) {
            return null;
        }

        // 读取压缩专用 provider 配置，缺失时回落到主 provider
        String provider = firstNonBlank(getValue("compression_provider"), getValue("provider"), "openai");
        String factory = firstNonBlank(getValue("compression_factory"), getValue("factory"), "default");

        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(provider)
                .baseUrl(firstNonBlank(getValue("compression_provider_base_url"), getValue("provider_base_url"), getValue("provider_api_address")))
                .appKey(firstNonBlank(getValue("compression_provider_api_key"), getValue("provider_api_key"), getValue("provider_app_key")))
                .appSecret(firstNonBlank(getValue("compression_provider_app_secret"), getValue("provider_app_secret"), getValue("provider_secret_key")))
                .build();

        ChatClientSettings compressionSettings = ChatClientSettings.builder()
                .factory(factory)
                .provider(provider)
                .defaultModel(compressionModel)
                .compressionModel(compressionModel)
                .timeoutMillis(getLong("timeout_millis", 30_000L))
                .baseSetting(setting)
                .build();

        return new DefaultScopeChatClient(compressionSettings);
    }

    @Override
    /** 获取Object */
    public ChatClient getObject() {
        return chatClient;
    }

    @Override
    public Class<?> getObjectType() {
        return ChatClient.class;
    }

    @Override
    /** 是否Singleton */
    public boolean isSingleton() {
        return true;
    }

    /** 获取Value */
    private String getValue(String field) {
        return sysAiGlobalSettingService.getValue(GROUP, field).orElse(null);
    }

    /** 获取Long */
    private long getLong(String field, long defaultValue) {
        String value = getValue(field);
        if (StringUtils.isBlank(value)) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /** FirstNonBlank */
    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) return v;
        }
        return null;
    }

    /** 关闭Quietly */
    private static void closeQuietly(ChatClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
