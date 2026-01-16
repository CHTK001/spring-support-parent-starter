package com.chua.starter.ai.support.chat;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.ai.support.mcp.McpPostprocessor;
import com.chua.starter.ai.support.mcp.McpPreprocessor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * LLM语言模型聊天客户端接口
 *
 * @author CH
 * @since 2024-01-01
 */
public interface ChatClient {

    /**
     * 发送聊天消息
     *
     * @param message 用户消息
     * @return LLM回复内容
     */
    String chat(String message);

    /**
     * 发送聊天消息（带上下文）
     *
     * @param message 用户消息
     * @param context 上下文信息
     * @return LLM回复内容
     */
    String chat(String message, ChatContext context);

    /**
     * 聊天客户端构建器
     */
    @Slf4j
    class Builder {

        private ChatClient chatClient;
        private List<McpPreprocessor> preprocessors;
        private List<McpPostprocessor> postprocessors;

        /**
         * 设置聊天客户端实现
         *
         * @param chatClient 聊天客户端
         * @return 构建器
         */
        public Builder client(ChatClient chatClient) {
            this.chatClient = chatClient;
            return this;
        }

        /**
         * 使用SPI加载前置处理器
         *
         * @return 构建器
         */
        public Builder withPreprocessors() {
            this.preprocessors = ServiceProvider.of(McpPreprocessor.class).collect();
            log.info("[AI][ChatClient] 加载前置处理器: {}", preprocessors.size());
            return this;
        }

        /**
         * 使用SPI加载后置处理器
         *
         * @return 构建器
         */
        public Builder withPostprocessors() {
            this.postprocessors = ServiceProvider.of(McpPostprocessor.class).collect();
            log.info("[AI][ChatClient] 加载后置处理器: {}", postprocessors.size());
            return this;
        }

        /**
         * 构建带MCP处理的聊天客户端
         *
         * @return 聊天客户端
         */
        public ChatClient build() {
            if (chatClient == null) {
                throw new IllegalStateException("ChatClient实现不能为空");
            }

            if (preprocessors == null || preprocessors.isEmpty()) {
                preprocessors = ServiceProvider.of(McpPreprocessor.class).collect();
            }
            if (postprocessors == null || postprocessors.isEmpty()) {
                postprocessors = ServiceProvider.of(McpPostprocessor.class).collect();
            }

            return new McpChatClient(chatClient, preprocessors, postprocessors);
        }
    }

    /**
     * 创建构建器
     *
     * @return 构建器
     */
    static Builder builder() {
        return new Builder();
    }
}

