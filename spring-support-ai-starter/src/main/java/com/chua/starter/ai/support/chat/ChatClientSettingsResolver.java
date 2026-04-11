package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.bigmodel.BigModelSetting;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.ai.support.configuration.AiProviderDefaults;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.properties.LlmProperties;
import com.chua.starter.ai.support.properties.ProviderProperties;

import java.util.function.Function;

/**
 * 聊天客户端配置解析器。
 *
 * @author CH
 * @since 2026/04/03
 */
public final class ChatClientSettingsResolver {

    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private ChatClientSettingsResolver() {
    }

    /**
     * 从 Spring 配置中解析聊天客户端静态配置。
     *
     * @param properties Spring AI 配置
     * @return 聊天客户端静态配置
     */
    public static ChatClientSettings resolve(AiProperties properties) {
        properties = AiProviderDefaults.normalize(properties);
        if (properties == null || properties.getLlm() == null || !properties.getLlm().isEnabled()) {
            throw new IllegalStateException("spring.ai.llm 必须启用后才能创建 ChatClient");
        }
        LlmProperties llm = properties.getLlm();
        String provider = firstNonBlank(llm.getProvider(), properties.getDefaultProvider());
        if (StringUtils.isBlank(provider)) {
            throw new IllegalStateException("spring.ai.llm.provider 或 spring.ai.default-provider 至少要配置一个");
        }
        ProviderProperties providerProperties = properties.getProviders().get(provider);
        BigModelSetting setting = BigModelSetting.builder()
                .provider(provider)
                .host(providerValue(providerProperties, ProviderProperties::getHost))
                .baseUrl(firstNonBlank(providerValue(providerProperties, ProviderProperties::getBaseUrl),
                        providerValue(providerProperties, ProviderProperties::getApiAddress)))
                .appId(providerValue(providerProperties, ProviderProperties::getAppId))
                .appKey(firstNonBlank(providerValue(providerProperties, ProviderProperties::getApiKey),
                        providerValue(providerProperties, ProviderProperties::getAppKey)))
                .appSecret(firstNonBlank(providerValue(providerProperties, ProviderProperties::getAppSecret),
                        providerValue(providerProperties, ProviderProperties::getSecretKey)))
                .encryptedKeyFile(providerValue(providerProperties, ProviderProperties::getEncryptedKeyFile))
                .keyPassword(providerValue(providerProperties, ProviderProperties::getKeyPassword))
                .envKey(providerValue(providerProperties, ProviderProperties::getEnvKey))
                .mcpEnabled(llm.getMcp() != null && llm.getMcp().isEnabled())
                .mcpPreprocessors(llm.getMcp() == null ? java.util.List.of() : safeList(llm.getMcp().getPreprocessors()))
                .mcpPostprocessors(llm.getMcp() == null ? java.util.List.of() : safeList(llm.getMcp().getPostprocessors()))
                .build();
        com.chua.common.support.ai.bigmodel.BigModelClient.normalize(setting);
        return ChatClientSettings.builder()
                .factory(firstNonBlank(llm.getFactory(), "default"))
                .provider(provider)
                .defaultModel(llm.getModel())
                .systemPrompt(llm.getSystem())
                .temperature(llm.getTemperature())
                .maxTokens(llm.getMaxTokens())
                .timeoutMillis(resolveTimeout(llm, providerProperties))
                .inputOptimizationEnabled(llm.isInputOptimizationEnabled())
                .contextCompressionEnabled(llm.isContextCompressionEnabled())
                .contextCompressionThreshold(llm.getContextCompressionThreshold())
                .contextCompressionRetainMessages(llm.getContextCompressionRetainMessages())
                .baseSetting(setting)
                .build();
    }

    /**
     * 将可能为空的列表转换为只读空列表语义。
     *
     * @param values 原始列表
     * @return 安全列表
     */
    private static java.util.List<String> safeList(java.util.List<String> values) {
        return values == null ? java.util.List.of() : values;
    }

    /**
     * 解析聊天超时时间。
     *
     * 优先级: `llm.timeout > provider.timeout > 默认值`
     *
     * @param llm LLM 配置
     * @param providerProperties provider 配置
     * @return 超时毫秒数
     */
    private static long resolveTimeout(LlmProperties llm, ProviderProperties providerProperties) {
        if (llm.getTimeout() != null) {
            return llm.getTimeout();
        }
        if (providerProperties != null && providerProperties.getTimeout() != null) {
            return providerProperties.getTimeout();
        }
        return DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param values 候选值列表
     * @return 第一个非空白值
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从 provider 配置中安全读取属性。
     *
     * @param providerProperties provider 配置
     * @param extractor 属性提取函数
     * @param <T> 属性类型
     * @return 属性值
     */
    private static <T> T providerValue(ProviderProperties providerProperties, Function<ProviderProperties, T> extractor) {
        return providerProperties == null ? null : extractor.apply(providerProperties);
    }
}
