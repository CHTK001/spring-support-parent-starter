package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.bigmodel.BigModelSetting;
import lombok.Builder;
import lombok.Getter;

/**
 * Spring 聊天客户端静态配置。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class ChatClientSettings {

    /**
     * ChatClient SPI 工厂名称。
     */
    private final String factory;

    /**
     * 固定提供商名称。
     */
    private final String provider;

    /**
     * 默认模型名称。
     */
    private final String defaultModel;

    /**
     * 默认系统提示词。
     */
    private final String systemPrompt;

    /**
     * 默认温度参数。
     */
    private final Double temperature;

    /**
     * 默认最大输出 token 数。
     */
    private final Integer maxTokens;

    /**
     * 默认超时时间，单位毫秒。
     */
    private final Long timeoutMillis;

    /**
     * 默认是否启用输入美化。
     */
    @Builder.Default
    private final boolean inputOptimizationEnabled = false;

    /**
     * 默认是否启用上下文压缩。
     */
    @Builder.Default
    private final boolean contextCompressionEnabled = false;

    /**
     * 默认上下文压缩阈值。
     */
    @Builder.Default
    private final int contextCompressionThreshold = 12;

    /**
     * 默认压缩后保留的尾部消息数量。
     */
    @Builder.Default
    private final int contextCompressionRetainMessages = 6;

    /**
     * 固定 provider 对应的大模型配置。
     */
    private final BigModelSetting baseSetting;

    /**
     * 创建本次调用实际使用的底层设置。
     *
     * 该方法只复制当前 provider 需要的稳定配置，并按 scope 重新决定是否启用 MCP。
     *
     * @param mcpEnabled 本次调用是否启用 MCP
     * @return 复制后的底层设置
     */
    public BigModelSetting createSetting(boolean mcpEnabled) {
        BigModelSetting source = baseSetting;
        BigModelSetting target = BigModelSetting.builder()
                .provider(source == null ? provider : source.getProvider())
                .host(source == null ? null : source.getHost())
                .appKey(source == null ? null : source.getAppKey())
                .baseUrl(source == null ? null : source.getBaseUrl())
                .appId(source == null ? null : source.getAppId())
                .appSecret(source == null ? null : source.getAppSecret())
                .encryptedKeyFile(source == null ? null : source.getEncryptedKeyFile())
                .keyPassword(source == null ? null : source.getKeyPassword())
                .envKey(source == null ? null : source.getEnvKey())
                .keyMode(source == null ? null : source.getKeyMode())
                .mcpEnabled(source != null && source.isMcpEnabled() && mcpEnabled)
                .mcpPreprocessors(source == null ? java.util.List.of() : source.getMcpPreprocessors())
                .mcpPostprocessors(source == null ? java.util.List.of() : source.getMcpPostprocessors())
                .defaultPricingMultiplier(source == null ? java.math.BigDecimal.ONE : source.getDefaultPricingMultiplier())
                .pricingMultipliers(source == null ? java.util.Map.of() : source.getPricingMultipliers())
                .build();
        com.chua.common.support.ai.bigmodel.BigModelClient.normalize(target);
        return target;
    }
}
