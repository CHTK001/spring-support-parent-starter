package com.chua.starter.ai.support.chat;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.deeplearning.support.ml.mcp.model.ChatContext;
import com.chua.starter.ai.support.mcp.ChatContextAdapter;
import com.chua.starter.ai.support.mcp.McpPostprocessor;
import com.chua.starter.ai.support.mcp.McpPreprocessor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Spring层聊天客户端接口
 * <p>
 * 继承自 utils 模块的 ChatClient，提供 Spring 层的扩展功能
 *
 * @author CH
 * @since 2024-01-01
 */
public interface ChatClient extends com.chua.deeplearning.support.api.ChatClient {

    /**
     * 发送聊天消息（带 Spring 层上下文）
     *
     * @param message 用户消息
     * @param context Spring 层上下文信息
     * @return LLM回复内容
     */
    default String chat(String message, com.chua.starter.ai.support.chat.ChatContext context) {
        var deepLearningContext = ChatContextAdapter.toDeepLearningContext(context);
        // 添加历史消息
        if (context != null && context.getHistory() != null) {
            for (var msg : context.getHistory()) {
                if ("user".equals(msg.getRole())) {
                    addUserHistory(msg.getContent());
                } else if ("assistant".equals(msg.getRole())) {
                    addAssistantHistory(msg.getContent());
                }
            }
        }
        return chatSync(message);
    }

    /**
     * 聊天客户端构建器
     */
    @Slf4j
    class Builder {

        private com.chua.deeplearning.support.api.ChatClient chatClient;
        private List<McpPreprocessor> preprocessors;
        private List<McpPostprocessor> postprocessors;

        /**
         * 设置聊天客户端实现
         *
         * @param chatClient 聊天客户端
         * @return 构建器
         */
        public Builder client(com.chua.deeplearning.support.api.ChatClient chatClient) {
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
