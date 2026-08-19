package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import lombok.Getter;
import lombok.Setter;

/**
 * ChatClient 创建设置。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Getter
@Setter
public class ChatClientSettings {

    /**
     * 厂商工厂类型
     */
    private String factory;

    /**
     * 厂商提供者
     */
    private String provider;

    /**
     * 默认模型
     */
    private String defaultModel;

    /**
     * 上下文压缩模型
     */
    private String compressionModel;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大生成 tokens
     */
    private Integer maxTokens;

    /**
     * 请求超时时间（毫秒）
     */
    private Long timeoutMillis;

    /**
     * 是否启用输入优化
     */
    private boolean inputOptimizationEnabled;

    /**
     * 是否启用上下文压缩
     */
    private boolean contextCompressionEnabled;

    /**
     * 上下文压缩触发阈值
     */
    private int contextCompressionThreshold;

    /**
     * 上下文压缩保留消息条数
     */
    private int contextCompressionRetainMessages;

    /**
     * 基础连接设置
     */
    private ChatClientSetting baseSetting;

    /**
     * 空参构造。
     */
    public ChatClientSettings() {
    }

    /**
     * 全参构造。
     *
     * @param factory                           厂商工厂类型
     * @param provider                          厂商提供者
     * @param defaultModel                      默认模型
     * @param compressionModel                  上下文压缩模型
     * @param systemPrompt                      系统提示词
     * @param temperature                       采样温度
     * @param maxTokens                         最大生成 tokens
     * @param timeoutMillis                     请求超时时间（毫秒）
     * @param inputOptimizationEnabled          是否启用输入优化
     * @param contextCompressionEnabled         是否启用上下文压缩
     * @param contextCompressionThreshold       上下文压缩触发阈值
     * @param contextCompressionRetainMessages  上下文压缩保留消息条数
     * @param baseSetting                       基础连接设置
     */
    public ChatClientSettings(String factory, String provider, String defaultModel, String compressionModel,
                              String systemPrompt, Double temperature, Integer maxTokens, Long timeoutMillis,
                              boolean inputOptimizationEnabled, boolean contextCompressionEnabled,
                              int contextCompressionThreshold, int contextCompressionRetainMessages,
                              ChatClientSetting baseSetting) {
        this.factory = factory;
        this.provider = provider;
        this.defaultModel = defaultModel;
        this.compressionModel = compressionModel;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutMillis = timeoutMillis;
        this.inputOptimizationEnabled = inputOptimizationEnabled;
        this.contextCompressionEnabled = contextCompressionEnabled;
        this.contextCompressionThreshold = contextCompressionThreshold;
        this.contextCompressionRetainMessages = contextCompressionRetainMessages;
        this.baseSetting = baseSetting;
    }

    /**
     * 创建 Builder。
     *
     * @return Builder 实例
     */
    public static ChatClientSettingsBuilder builder() {
        return new ChatClientSettingsBuilder();
    }

    /**
     * 基于基础设置创建连接设置（MCP 透传基础连接信息）。
     *
     * @param mcpEnabled 是否启用 MCP
     * @return 连接设置
     */
    public ChatClientSetting createSetting(boolean mcpEnabled) {
        if (baseSetting == null) {
            return ChatClientSetting.builder().build();
        }
        return ChatClientSetting.builder()
                .provider(baseSetting.getProvider())
                .baseUrl(baseSetting.getBaseUrl())
                .appKey(baseSetting.getAppKey())
                .appSecret(baseSetting.getAppSecret())
                .build();
    }

    /**
     * ChatClientSettings Builder。
     *
     * @since 4.0.0.42
     */
    public static class ChatClientSettingsBuilder {

        /**
         * 厂商工厂类型
         */
        private String factory;

        /**
         * 厂商提供者
         */
        private String provider;

        /**
         * 默认模型
         */
        private String defaultModel;

        /**
         * 上下文压缩模型
         */
        private String compressionModel;

        /**
         * 系统提示词
         */
        private String systemPrompt;

        /**
         * 采样温度
         */
        private Double temperature;

        /**
         * 最大生成 tokens
         */
        private Integer maxTokens;

        /**
         * 请求超时时间（毫秒）
         */
        private Long timeoutMillis;

        /**
         * 是否启用输入优化
         */
        private boolean inputOptimizationEnabled;

        /**
         * 是否启用上下文压缩
         */
        private boolean contextCompressionEnabled;

        /**
         * 上下文压缩触发阈值
         */
        private int contextCompressionThreshold;

        /**
         * 上下文压缩保留消息条数
         */
        private int contextCompressionRetainMessages;

        /**
         * 基础连接设置
         */
        private ChatClientSetting baseSetting;

        /**
         * 设置厂商工厂类型。
         *
         * @param factory 厂商工厂类型
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder factory(String factory) {
            this.factory = factory;
            return this;
        }

        /**
         * 设置厂商提供者。
         *
         * @param provider 厂商提供者
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置默认模型。
         *
         * @param defaultModel 默认模型
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        /**
         * 设置上下文压缩模型。
         *
         * @param compressionModel 上下文压缩模型
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder compressionModel(String compressionModel) {
            this.compressionModel = compressionModel;
            return this;
        }

        /**
         * 设置系统提示词。
         *
         * @param systemPrompt 系统提示词
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * 设置采样温度。
         *
         * @param temperature 采样温度
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 设置最大生成 tokens。
         *
         * @param maxTokens 最大生成 tokens
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 设置请求超时时间。
         *
         * @param timeoutMillis 请求超时时间（毫秒）
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder timeoutMillis(Long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * 设置是否启用输入优化。
         *
         * @param inputOptimizationEnabled 是否启用输入优化
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder inputOptimizationEnabled(boolean inputOptimizationEnabled) {
            this.inputOptimizationEnabled = inputOptimizationEnabled;
            return this;
        }

        /**
         * 设置是否启用上下文压缩。
         *
         * @param contextCompressionEnabled 是否启用上下文压缩
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder contextCompressionEnabled(boolean contextCompressionEnabled) {
            this.contextCompressionEnabled = contextCompressionEnabled;
            return this;
        }

        /**
         * 设置上下文压缩触发阈值。
         *
         * @param contextCompressionThreshold 上下文压缩触发阈值
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder contextCompressionThreshold(int contextCompressionThreshold) {
            this.contextCompressionThreshold = contextCompressionThreshold;
            return this;
        }

        /**
         * 设置上下文压缩保留消息条数。
         *
         * @param contextCompressionRetainMessages 上下文压缩保留消息条数
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder contextCompressionRetainMessages(int contextCompressionRetainMessages) {
            this.contextCompressionRetainMessages = contextCompressionRetainMessages;
            return this;
        }

        /**
         * 设置基础连接设置。
         *
         * @param baseSetting 基础连接设置
         * @return 当前 Builder
         */
        public ChatClientSettingsBuilder baseSetting(ChatClientSetting baseSetting) {
            this.baseSetting = baseSetting;
            return this;
        }

        /**
         * 构建 ChatClientSettings 实例。
         *
         * @return ChatClientSettings 实例
         */
        public ChatClientSettings build() {
            return new ChatClientSettings(factory, provider, defaultModel, compressionModel, systemPrompt,
                    temperature, maxTokens, timeoutMillis, inputOptimizationEnabled, contextCompressionEnabled,
                    contextCompressionThreshold, contextCompressionRetainMessages, baseSetting);
        }
    }
}
