package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatResponse.State;
import com.chua.common.support.ai.chat.ModelDefinition;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默认 ChatClient 实现（模拟响应）。
 *
 * <p>支持对象池: 通过 {@code pool(Number)} 调整池大小, 默认单例模式。
 * 由于本类无需池化底层客户端 (本身就是 mock), pool() 的默认实现 (no-op + 返回 this) 已足够。
 * 如需真实池化, 请使用 {@link PooledChatClient} 包装。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DefaultChatClient implements ChatClient {

    /**
     * 模型名称
     */
    private final String modelName;

    /**
     * 厂商工厂类型
     */
    private final String factory;

    /**
     * 厂商提供者
     */
    private final String provider;

    /**
     * 简易构造，默认使用 default/mock 工厂与提供者。
     *
     * @param modelName 模型名称
     */
    public DefaultChatClient(String modelName) {
        this(modelName, "default", "mock");
    }

    /**
     * 全参构造。
     *
     * @param modelName 模型名称
     * @param factory   厂商工厂类型
     * @param provider  厂商提供者
     */
    public DefaultChatClient(String modelName, String factory, String provider) {
        this.modelName = modelName;
        this.factory = factory;
        this.provider = provider;
    }

    /**
     * 发送单条消息并返回模拟响应。
     *
     * @param message 用户消息
     * @return 模拟响应
     */
    public ChatResponse send(String message) {
        log.info("[AI] 模拟响应: {}", message);
        return ChatResponse.builder()
                .state(State.STOP)
                .content("AI 响应（模拟）: " + message)
                .build();
    }

    /**
     * 携带系统提示词发送消息并返回模拟响应。
     *
     * @param systemPrompt 系统提示词
     * @param message      用户消息
     * @return 模拟响应
     */
    public ChatResponse send(String systemPrompt, String message) {
        log.info("[AI] 系统提示: {}, 消息: {}", systemPrompt, message);
        return send(message);
    }

    /**
     * 获取模型名称。
     *
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取厂商工厂类型。
     *
     * @return 厂商工厂类型
     */
    public String getFactory() {
        return factory;
    }

    /**
     * 获取厂商提供者。
     *
     * @return 厂商提供者
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 获取默认模型名称。
     *
     * @return 模型名称
     */
    public String getDefaultModel() {
        return modelName;
    }

    /**
     * 获取服务端点，模拟实现返回 null。
     *
     * @return 服务端点
     */
    public String getEndpoint() {
        return null;
    }

    /**
     * 获取模型定义列表，模拟实现返回空列表。
     *
     * @return 模型定义列表
     */
    public List<ModelDefinition> listModelDefinitions() {
        return List.of();
    }

    @Override
    /** Chat */
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
    /** ChatSync */
    public String chatSync(String input) {
        return "AI 响应（模拟）: " + input;
    }

    /** ChatSync */
    public String chatSync(String input, long timeoutMillis) {
        return chatSync(input);
    }

    @Override
    /** 关闭 */
    public void close() {
    }

    @Override
    /** Chat */
    public ChatResponse chat(ChatScope scope) {
        String input = scope != null ? scope.getInput() : "";
        return ChatResponse.builder()
                .state(State.STOP)
                .content("AI 响应（模拟）: " + input)
                .build();
    }

    @Override
    /** Chat */
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
    /** ListTools */
    public java.util.List<AiTool> listTools(ChatScope scope) {
        return List.of();
    }

    @Override
    /** 调用Tool */
    public AiToolResult callTool(ChatScope scope, AiToolCall toolCall) {
        return new AiToolResult();
    }
}
