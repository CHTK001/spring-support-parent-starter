package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatResponse.State;
import com.chua.common.support.ai.chat.ModelDefinition;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DefaultChatClient implements ChatClient {

    private final String modelName;
    private final String factory;
    private final String provider;

    public DefaultChatClient(String modelName) {
        this(modelName, "default", "mock");
    }

    public DefaultChatClient(String modelName, String factory, String provider) {
        this.modelName = modelName;
        this.factory = factory;
        this.provider = provider;
    }

    public ChatResponse send(String message) {
        log.info("[AI] 模拟响应: {}", message);
        return ChatResponse.builder()
                .state(State.STOP)
                .content("AI 响应（模拟）: " + message)
                .build();
    }

    public ChatResponse send(String systemPrompt, String message) {
        log.info("[AI] 系统提示: {}, 消息: {}", systemPrompt, message);
        return send(message);
    }

    public String getModelName() {
        return modelName;
    }

    public String getFactory() {
        return factory;
    }

    public String getProvider() {
        return provider;
    }

    public String getDefaultModel() {
        return modelName;
    }

    public String getEndpoint() {
        return null;
    }

    public List<ModelDefinition> listModels() {
        return List.of();
    }

    @Override
    public void chat(String input, java.util.function.Consumer<ChatResponse> consumer, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        try {
            ChatResponse response = ChatResponse.builder()
                    .state(State.STOP)
                    .content("AI 响应（模拟）: " + input)
                    .build();
            if (consumer != null) {
                consumer.accept(response);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (Throwable t) {
            if (onError != null) {
                onError.accept(t);
            }
        }
    }

    @Override
    public String chatSync(String input) {
        return "AI 响应（模拟）: " + input;
    }

    public String chatSync(String input, long timeoutMillis) {
        return chatSync(input);
    }

    @Override
    public void close() {
    }

    @Override
    public ChatResponse chat(ChatScope scope) {
        String input = scope != null ? scope.getInput() : "";
        return ChatResponse.builder()
                .state(State.STOP)
                .content("AI 响应（模拟）: " + input)
                .build();
    }

    @Override
    public void chat(ChatScope scope, java.util.function.Consumer<String> consumer, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        ChatResponse response = chat(scope);
        if (consumer != null && response != null) {
            consumer.accept(response.getContent());
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    @Override
    public java.util.List<AiTool> listTools(ChatScope scope) {
        return List.of();
    }

    @Override
    public AiToolResult callTool(ChatScope scope, AiToolCall toolCall) {
        return new AiToolResult();
    }
}