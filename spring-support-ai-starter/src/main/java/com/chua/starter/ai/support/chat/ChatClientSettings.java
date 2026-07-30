package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatClientSettings {

    private String factory;
    private String provider;
    private String defaultModel;
    private String compressionModel;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Long timeoutMillis;
    private boolean inputOptimizationEnabled;
    private boolean contextCompressionEnabled;
    private int contextCompressionThreshold;
    private int contextCompressionRetainMessages;
    private ChatClientSetting baseSetting;

    public ChatClientSettings() {
    }

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

    public static ChatClientSettingsBuilder builder() {
        return new ChatClientSettingsBuilder();
    }

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

    public static class ChatClientSettingsBuilder {
        private String factory;
        private String provider;
        private String defaultModel;
        private String compressionModel;
        private String systemPrompt;
        private Double temperature;
        private Integer maxTokens;
        private Long timeoutMillis;
        private boolean inputOptimizationEnabled;
        private boolean contextCompressionEnabled;
        private int contextCompressionThreshold;
        private int contextCompressionRetainMessages;
        private ChatClientSetting baseSetting;

        public ChatClientSettingsBuilder factory(String factory) { this.factory = factory; return this; }
        public ChatClientSettingsBuilder provider(String provider) { this.provider = provider; return this; }
        public ChatClientSettingsBuilder defaultModel(String defaultModel) { this.defaultModel = defaultModel; return this; }
        public ChatClientSettingsBuilder compressionModel(String compressionModel) { this.compressionModel = compressionModel; return this; }
        public ChatClientSettingsBuilder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public ChatClientSettingsBuilder temperature(Double temperature) { this.temperature = temperature; return this; }
        public ChatClientSettingsBuilder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public ChatClientSettingsBuilder timeoutMillis(Long timeoutMillis) { this.timeoutMillis = timeoutMillis; return this; }
        public ChatClientSettingsBuilder inputOptimizationEnabled(boolean inputOptimizationEnabled) { this.inputOptimizationEnabled = inputOptimizationEnabled; return this; }
        public ChatClientSettingsBuilder contextCompressionEnabled(boolean contextCompressionEnabled) { this.contextCompressionEnabled = contextCompressionEnabled; return this; }
        public ChatClientSettingsBuilder contextCompressionThreshold(int contextCompressionThreshold) { this.contextCompressionThreshold = contextCompressionThreshold; return this; }
        public ChatClientSettingsBuilder contextCompressionRetainMessages(int contextCompressionRetainMessages) { this.contextCompressionRetainMessages = contextCompressionRetainMessages; return this; }
        public ChatClientSettingsBuilder baseSetting(ChatClientSetting baseSetting) { this.baseSetting = baseSetting; return this; }

        public ChatClientSettings build() {
            return new ChatClientSettings(factory, provider, defaultModel, compressionModel, systemPrompt,
                    temperature, maxTokens, timeoutMillis, inputOptimizationEnabled, contextCompressionEnabled,
                    contextCompressionThreshold, contextCompressionRetainMessages, baseSetting);
        }
    }
}