package com.chua.starter.ai.support.chat;

import com.chua.deeplearning.support.ml.bigmodel.BigModelCallback;
import com.chua.deeplearning.support.ml.bigmodel.BigModelClient;
import com.chua.deeplearning.support.ml.bigmodel.BigModelRequest;
import com.chua.deeplearning.support.ml.bigmodel.BigModelSetting;
import com.chua.deeplearning.support.ml.mcp.model.ChatContext;
import com.chua.starter.ai.support.mcp.ChatContextAdapter;
import com.chua.starter.ai.support.mcp.McpPostprocessor;
import com.chua.starter.ai.support.mcp.McpPreprocessor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 带MCP前置后置处理的聊天客户端包装器
 *
 * @author CH
 * @since 2024-01-01
 */
@Slf4j
class McpChatClient implements ChatClient {

    private final com.chua.deeplearning.support.api.ChatClient delegate;
    private final List<McpPreprocessor> preprocessors;
    private final List<McpPostprocessor> postprocessors;

    McpChatClient(com.chua.deeplearning.support.api.ChatClient delegate,
                  List<McpPreprocessor> preprocessors,
                  List<McpPostprocessor> postprocessors) {
        this.delegate = delegate;
        this.preprocessors = preprocessors.stream()
                .sorted(Comparator.comparingInt(McpPreprocessor::getPriority))
                .toList();
        this.postprocessors = postprocessors.stream()
                .sorted(Comparator.comparingInt(McpPostprocessor::getPriority))
                .toList();
    }

    @Override
    public String chat(String message, com.chua.starter.ai.support.chat.ChatContext context) {
        var dlContext = ChatContextAdapter.toDeepLearningContext(context);
        return chatWithProcessing(message, dlContext);
    }

    /**
     * 带处理器的聊天
     *
     * @param message 消息
     * @param context 上下文
     * @return 响应
     */
    private String chatWithProcessing(String message, ChatContext context) {
        var processedInput = message;
        for (var preprocessor : preprocessors) {
            try {
                processedInput = preprocessor.preprocess(processedInput, context);
                log.debug("[AI][MCP] 前置处理: {} -> {}", message, processedInput);
            } catch (Exception e) {
                log.warn("[AI][MCP] 前置处理器执行失败: {}", e.getMessage());
            }
        }

        var rawOutput = delegate.chatSync(processedInput);

        var processedOutput = rawOutput;
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

    @Override
    public ChatClient model(String model) {
        delegate.model(model);
        return this;
    }

    @Override
    public ChatClient system(String system) {
        delegate.system(system);
        return this;
    }

    @Override
    public ChatClient temperature(double temperature) {
        delegate.temperature(temperature);
        return this;
    }

    @Override
    public ChatClient maxTokens(int maxTokens) {
        delegate.maxTokens(maxTokens);
        return this;
    }

    @Override
    public ChatClient addImage(String imageUrl) {
        delegate.addImage(imageUrl);
        return this;
    }

    @Override
    public ChatClient addUserHistory(String content) {
        delegate.addUserHistory(content);
        return this;
    }

    @Override
    public ChatClient addAssistantHistory(String content) {
        delegate.addAssistantHistory(content);
        return this;
    }

    @Override
    public ChatClient clearHistory() {
        delegate.clearHistory();
        return this;
    }

    @Override
    public void chat(String prompt, Consumer<String> consumer) {
        delegate.chat(prompt, consumer);
    }

    @Override
    public void chat(String prompt, Consumer<String> consumer, Runnable onComplete) {
        delegate.chat(prompt, consumer, onComplete);
    }

    @Override
    public void chat(String prompt, Consumer<String> consumer, Runnable onComplete, Consumer<Throwable> onError) {
        delegate.chat(prompt, consumer, onComplete, onError);
    }

    @Override
    public void chat(BigModelRequest request, BigModelCallback callback) {
        delegate.chat(request, callback);
    }

    @Override
    public String chatSync(String prompt) {
        return chatWithProcessing(prompt, new ChatContext());
    }

    @Override
    public String chatSync(String prompt, long timeout) {
        var context = new ChatContext();
        var processedInput = prompt;
        for (var preprocessor : preprocessors) {
            try {
                processedInput = preprocessor.preprocess(processedInput, context);
            } catch (Exception e) {
                log.warn("[AI][MCP] 前置处理器执行失败: {}", e.getMessage());
            }
        }

        var rawOutput = delegate.chatSync(processedInput, timeout);

        var processedOutput = rawOutput;
        for (var postprocessor : postprocessors) {
            try {
                processedOutput = postprocessor.postprocess(processedOutput, context);
            } catch (Exception e) {
                log.warn("[AI][MCP] 后置处理器执行失败: {}", e.getMessage());
            }
        }

        return processedOutput;
    }

    @Override
    public CompletableFuture<String> chatAsync(String prompt) {
        return delegate.chatAsync(prompt);
    }

    @Override
    public CompletableFuture<String> chatAsync(String prompt, long timeout) {
        return delegate.chatAsync(prompt, timeout);
    }

    @Override
    public BigModelClient getClient() {
        return delegate.getClient();
    }

    @Override
    public BigModelSetting getSetting() {
        return delegate.getSetting();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
