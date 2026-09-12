package com.chua.starter.ai.support.chat;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
* 对话作用域，封装单次对话的模型参数、上下文与附加信息。
*
* @since 4.0.0.42
* @author CH
 */
@Getter
@Setter
public class ChatScope {

    /**
    * 系统提示词
     */
    private String systemPrompt;

    /**
    * 模型名称
     */
    private String model;

    /**
    * 采样温度
     */
    private Double temperature;

    /**
    * 最大生成 令牌
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
    * 是否启用 MCP 工具
     */
    private boolean mcpEnabled;

    /**
    * 图片地址列表
     */
    private List<String> imageUrls;

    /**
    * 用户输入
     */
    private String input;

    /**
    * 对话上下文
     */
    private ChatContext context;

    /**
    * 扩展参数
     */
    private Map<String, Object> parameters;

    /**
    * 空参构造。
     */
    public ChatScope() {
    }

    /**
    * 全参构造。
    *
    * @param systemPrompt                    系统提示词
    * @param model                           模型名称
    * @param temperature                     采样温度
    * @param maxTokens                       最大生成 令牌
    * @param timeoutMillis                   请求超时时间（毫秒）
    * @param inputOptimizationEnabled        是否启用输入优化
    * @param contextCompressionEnabled       是否启用上下文压缩
    * @param contextCompressionThreshold     上下文压缩触发阈值
    * @param contextCompressionRetainMessages 上下文压缩保留消息条数
    * @param mcpEnabled                      是否启用 MCP 工具
    * @param imageUrls                       图片地址列表
    * @param input                           用户输入
    * @param context                         对话上下文
    * @param parameters                      扩展参数
     */
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

    /**
    * 创建 构建器。
    *
    * @return Builder 实例
     */
    public static ChatScopeBuilder builder() {
        /**
        * 对话scope构建器。
        * @return 对话scope构建器的结果
         */
        return new ChatScopeBuilder();
    }

    /**
    * 基于当前实例创建 构建器。
    *
    * @return Builder 实例
     */
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

    /**
    * 对话scope 构建器。
    *
    * @since 4.0.0.42
    * @author CH
     */
    public static class ChatScopeBuilder {

        /**
        * 系统提示词
         */
        private String systemPrompt;

        /**
        * 模型名称
         */
        private String model;

        /**
        * 采样温度
         */
        private Double temperature;

        /**
        * 最大生成 令牌
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
        * 是否启用 MCP 工具
         */
        private boolean mcpEnabled;

        /**
        * 图片地址列表
         */
        private List<String> imageUrls;

        /**
        * 用户输入
         */
        private String input;

        /**
        * 对话上下文
         */
        private ChatContext context;

        /**
        * 扩展参数
         */
        private Map<String, Object> parameters;

        /**
        * 设置系统提示词。
        *
        * @param systemPrompt 系统提示词
        * @return 当前 构建器
         */
        public ChatScopeBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
        * 设置模型名称。
        *
        * @param model 模型名称
        * @return 当前 构建器
         */
        public ChatScopeBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
        * 设置采样温度。
        *
        * @param temperature 采样温度
        * @return 当前 构建器
         */
        public ChatScopeBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
        * 设置最大生成 令牌。
        *
        * @param maxTokens 最大生成 令牌
        * @return 当前 构建器
         */
        public ChatScopeBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
        * 设置请求超时时间。
        *
        * @param timeoutMillis 请求超时时间（毫秒）
        * @return 当前 构建器
         */
        public ChatScopeBuilder timeoutMillis(Long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
        * 设置是否启用输入优化。
        *
        * @param inputOptimizationEnabled 是否启用输入优化
        * @return 当前 构建器
         */
        public ChatScopeBuilder inputOptimizationEnabled(boolean inputOptimizationEnabled) {
            this.inputOptimizationEnabled = inputOptimizationEnabled;
            return this;
        }

        /**
        * 设置是否启用上下文压缩。
        *
        * @param contextCompressionEnabled 是否启用上下文压缩
        * @return 当前 构建器
         */
        public ChatScopeBuilder contextCompressionEnabled(boolean contextCompressionEnabled) {
            this.contextCompressionEnabled = contextCompressionEnabled;
            return this;
        }

        /**
        * 设置上下文压缩触发阈值。
        *
        * @param contextCompressionThreshold 上下文压缩触发阈值
        * @return 当前 构建器
         */
        public ChatScopeBuilder contextCompressionThreshold(int contextCompressionThreshold) {
            this.contextCompressionThreshold = contextCompressionThreshold;
            return this;
        }

        /**
        * 设置上下文压缩保留消息条数。
        *
        * @param contextCompressionRetainMessages 上下文压缩保留消息条数
        * @return 当前 构建器
         */
        public ChatScopeBuilder contextCompressionRetainMessages(int contextCompressionRetainMessages) {
            this.contextCompressionRetainMessages = contextCompressionRetainMessages;
            return this;
        }

        /**
        * 设置是否启用 MCP 工具。
        *
        * @param mcpEnabled 是否启用 MCP 工具
        * @return 当前 构建器
         */
        public ChatScopeBuilder mcpEnabled(boolean mcpEnabled) {
            this.mcpEnabled = mcpEnabled;
            return this;
        }

        /**
        * 设置图片地址列表。
        *
        * @param imageUrls 图片地址列表
        * @return 当前 构建器
         */
        public ChatScopeBuilder imageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
            return this;
        }

        /**
        * 设置用户输入。
        *
        * @param input 用户输入
        * @return 当前 构建器
         */
        public ChatScopeBuilder input(String input) {
            this.input = input;
            return this;
        }

        /**
        * 设置对话上下文。
        *
        * @param context 对话上下文
        * @return 当前 构建器
         */
        public ChatScopeBuilder context(ChatContext context) {
            this.context = context;
            return this;
        }

        /**
        * 设置扩展参数。
        *
        * @param parameters 扩展参数
        * @return 当前 构建器
         */
        public ChatScopeBuilder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
        * 构建 对话scope 实例。
        *
        * @return ChatScope 实例
         */
        public ChatScope build() {
            return new ChatScope(systemPrompt, model, temperature, maxTokens, timeoutMillis,
                    inputOptimizationEnabled, contextCompressionEnabled, contextCompressionThreshold,
                    contextCompressionRetainMessages, mcpEnabled, imageUrls, input, context, parameters);
        }
    }
}
