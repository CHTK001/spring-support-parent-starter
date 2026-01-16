package com.chua.starter.ai.support.chat;

import com.chua.starter.ai.support.mcp.McpPostprocessor;
import com.chua.starter.ai.support.mcp.McpPreprocessor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * 带MCP前置后置处理的聊天客户端包装器
 *
 * @author CH
 * @since 2024-01-01
 */
@Slf4j
class McpChatClient implements ChatClient {

    private final ChatClient delegate;
    private final List<McpPreprocessor> preprocessors;
    private final List<McpPostprocessor> postprocessors;

    McpChatClient(ChatClient delegate, List<McpPreprocessor> preprocessors, List<McpPostprocessor> postprocessors) {
        this.delegate = delegate;
        this.preprocessors = preprocessors.stream()
                .sorted(Comparator.comparingInt(McpPreprocessor::getPriority))
                .toList();
        this.postprocessors = postprocessors.stream()
                .sorted(Comparator.comparingInt(McpPostprocessor::getPriority))
                .toList();
    }

    @Override
    public String chat(String message) {
        return chat(message, new ChatContext());
    }

    @Override
    public String chat(String message, ChatContext context) {
        // 前置处理
        String processedInput = message;
        for (var preprocessor : preprocessors) {
            try {
                processedInput = preprocessor.preprocess(processedInput, context);
                log.debug("[AI][MCP] 前置处理: {} -> {}", message, processedInput);
            } catch (Exception e) {
                log.warn("[AI][MCP] 前置处理器执行失败: {}", e.getMessage());
            }
        }

        // LLM处理
        String rawOutput = delegate.chat(processedInput, context);

        // 后置处理
        String processedOutput = rawOutput;
        for (var postprocessor : postprocessors) {
            try {
                processedOutput = postprocessor.postprocess(processedOutput, context);
                log.debug("[AI][MCP] 后置处理: {} -> {}", rawOutput, processedOutput);
            } catch (Exception e) {
                log.warn("[AI][MCP] 后置处理器执行失败: {}", e.getMessage());
            }
        }

        return processedOutput;
    }
}

