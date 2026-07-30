package com.chua.starter.ai.support.chat;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ChatScope {

    private String systemPrompt;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Long timeoutMillis;
    private boolean inputOptimizationEnabled;
    private boolean contextCompressionEnabled;
    private int contextCompressionThreshold;
    private int contextCompressionRetainMessages;
    private boolean mcpEnabled;
    private List<String> imageUrls;
    private String input;
    private ChatContext context;
    private Map<String, Object> parameters;

    public ChatScope() {
    }

    public ChatScope(String systemPrompt, String model, Double temperature, Integer maxTokens, Long timeoutMillis,
                     boolean inputOptimizationEnabled, boolean contextCompressionEnabled,
                     int contextCompressionThreshold, int contextCompressionRetainMessages,
                     boolean mcpEnabled, List<String> imageUrls, String input, ChatContext context,
                     Map<String, Object> parameters) {
        this.systemPrompt = systemPrompt;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutMillis = timeoutMillis;
        this.inputOptimizationEnabled = inputOptimizationEnabled;
        this.contextCompressionEnabled = contextCompressionEnabled;
        this.contextCompressionThreshold = contextCompressionThreshold;
        this.contextCompressionRetainMessages = contextCompressionRetainMessages;
        this.mcpEnabled = mcpEnabled;
        this.imageUrls = imageUrls;
        this.input = input;
        this.context = context;
        this.parameters = parameters;
    }

    public static ChatScopeBuilder builder() {
        return new ChatScopeBuilder();
    }

    public ChatScopeBuilder toBuilder() {
        return ChatScope.builder()
                .systemPrompt(systemPrompt)
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeoutMillis(timeoutMillis)
                .inputOptimizationEnabled(inputOptimizationEnabled)
                .contextCompressionEnabled(contextCompressionEnabled)
                .contextCompressionThreshold(contextCompressionThreshold)
                .contextCompressionRetainMessages(contextCompressionRetainMessages)
                .mcpEnabled(mcpEnabled)
                .imageUrls(imageUrls)
                .input(input)
                .context(context)
                .parameters(parameters);
    }

    public static class ChatScopeBuilder {
        private String systemPrompt;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Long timeoutMillis;
        private boolean inputOptimizationEnabled;
        private boolean contextCompressionEnabled;
        private int contextCompressionThreshold;
        private int contextCompressionRetainMessages;
        private boolean mcpEnabled;
        private List<String> imageUrls;
        private String input;
        private ChatContext context;
        private Map<String, Object> parameters;

        public ChatScopeBuilder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public ChatScopeBuilder model(String model) { this.model = model; return this; }
        public ChatScopeBuilder temperature(Double temperature) { this.temperature = temperature; return this; }
        public ChatScopeBuilder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public ChatScopeBuilder timeoutMillis(Long timeoutMillis) { this.timeoutMillis = timeoutMillis; return this; }
        public ChatScopeBuilder inputOptimizationEnabled(boolean inputOptimizationEnabled) { this.inputOptimizationEnabled = inputOptimizationEnabled; return this; }
        public ChatScopeBuilder contextCompressionEnabled(boolean contextCompressionEnabled) { this.contextCompressionEnabled = contextCompressionEnabled; return this; }
        public ChatScopeBuilder contextCompressionThreshold(int contextCompressionThreshold) { this.contextCompressionThreshold = contextCompressionThreshold; return this; }
        public ChatScopeBuilder contextCompressionRetainMessages(int contextCompressionRetainMessages) { this.contextCompressionRetainMessages = contextCompressionRetainMessages; return this; }
        public ChatScopeBuilder mcpEnabled(boolean mcpEnabled) { this.mcpEnabled = mcpEnabled; return this; }
        public ChatScopeBuilder imageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; return this; }
        public ChatScopeBuilder input(String input) { this.input = input; return this; }
        public ChatScopeBuilder context(ChatContext context) { this.context = context; return this; }
        public ChatScopeBuilder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }

        public ChatScope build() {
            return new ChatScope(systemPrompt, model, temperature, maxTokens, timeoutMillis,
                    inputOptimizationEnabled, contextCompressionEnabled, contextCompressionThreshold,
                    contextCompressionRetainMessages, mcpEnabled, imageUrls, input, context, parameters);
        }
    }
}
