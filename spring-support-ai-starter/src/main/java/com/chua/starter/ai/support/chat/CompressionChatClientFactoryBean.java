package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.springframework.beans.factory.FactoryBean;

/**
 * 压缩专用 对话客户端 工厂Bean，支持运行时重建。
 * <p>
 * 读取 sys_AI_全局_setting(runtime) 中的 compression_模型 和压缩专用 提供者 配置，
 * 如果 compression_模型 为空则不创建压缩专用 对话客户端（回落到主 对话客户端）。
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
     * 当前生效的压缩 对话客户端（可能为 空，回落到主 对话客户端）
     */
    private volatile ChatClient chatClient;

    /**
     * 构造压缩 对话客户端 工厂Bean，启动即尝试构建。
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

    /**
    * 构建客户端safe
    *
    * @return 构建客户端safe的结果
    */
    private ChatClient buildClientSafe() {
        try {
            return buildClient();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CompressionChatClientFactoryBean.class)
                    .warn("[CompressionChatClient] 构建失败，压缩将回落到主 ChatClient: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建客户端
     *
     * @return 构建客户端的结果
     */
    private ChatClient buildClient() {
        String compressionModel = getValue("compression_model");
        if (StringUtils.isBlank(compressionModel)) {
            return null;
        }

 // 读取压缩专用 提供者 配置，缺失时回落到主 提供者
        String provider = firstNonBlank(getValue("compression_provider"), getValue("provider"), "openai");
        String factory = firstNonBlank(getValue("compression_factory"), getValue("factory"), "default");

        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(provider)
                .baseUrl(firstNonBlank(getValue("compression_provider_base_url"), getValue("provider_base_url"), getValue("provider_api_address")))
                .appKey(firstNonBlank(getValue("compression_provider_api_key"), getValue("provider_api_key"), getValue("provider_app_key")))
                .appSecret(firstNonBlank(getValue("compression_provider_app_secret"), getValue("provider_app_secret"), getValue("provider_secret_key")))
                .proxy(firstNonBlank(getValue("compression_provider_proxy"), getValue("provider_proxy")))
                .build();

        ChatClientSettings compressionSettings = ChatClientSettings.builder()
                .factory(factory)
                .provider(provider)
                .defaultModel(compressionModel)
                .compressionModel(compressionModel)
                .timeoutMillis(getLong("timeout_millis", 30_000L))
                .baseSetting(setting)
                .build();

        return new PooledChatClient(() -> (ChatClient) CommonChatClientFactoryResolver.create(factory, compressionSettings.getBaseSetting()));
    }

    @Override
    /** 获取对象 */
    public ChatClient getObject() {
        return chatClient;
    }

    @Override
    public Class<?> getObjectType() {
        return ChatClient.class;
    }

    @Override
    /** 是否单例 */
    public boolean isSingleton() {
        return true;
    }

    /**
     * 获取值
     *
     * @param field 字段
     * @return 获取值的结果
     */
    private String getValue(String field) {
        return sysAiGlobalSettingService.getValue(GROUP, field).orElse(null);
    }

    /**
     * 获取Long
     *
     * @param field 字段
     * @param defaultValue 默认值
     * @return 获取long的结果
     */
    private long getLong(String field, long defaultValue) {
        String value = getValue(field);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * 第一个nonblank
     *
     * @param values 值
     * @return 第一个nonblank的结果
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * 关闭Quietly
     *
     * @param client 客户端
     */
    private static void closeQuietly(ChatClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
