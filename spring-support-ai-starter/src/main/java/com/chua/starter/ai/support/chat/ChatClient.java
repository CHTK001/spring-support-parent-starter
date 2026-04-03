package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.bigmodel.BigModelMetadataView;

import java.util.List;
import java.util.function.Consumer;

/**
 * Spring 层聊天客户端。
 * <p>
 * 该接口只负责单次请求 scope，不承载 session。
 *
 * @author CH
 * @since 2026/04/03
 */
public interface ChatClient extends AutoCloseable {

    /**
     * 当前聊天客户端工厂名称。
     *
     * @return 工厂名称
     */
    String getFactory();

    /**
     * 当前提供商名称。
     *
     * @return 提供商名称
     */
    String getProvider();

    /**
     * 默认模型名称。
     *
     * @return 默认模型名称
     */
    String getDefaultModel();

    /**
     * 当前底层端点。
     *
     * @return 端点地址
     */
    default String getEndpoint() {
        return null;
    }

    /**
     * 返回当前 provider 的模型目录。
     *
     * @return 模型目录
     */
    List<BigModelMetadataView> listModels();

    /**
     * 执行一次 scope 聊天。
     *
     * @param scope 聊天 scope
     * @return 聊天响应
     */
    ChatResponse chat(ChatScope scope);

    /**
     * 以流式方式执行一次 scope 聊天。
     *
     * @param scope      聊天 scope
     * @param consumer   输出消费者
     * @param onComplete 完成回调
     * @param onError    错误回调
     */
    void chat(ChatScope scope, Consumer<String> consumer, Runnable onComplete, Consumer<Throwable> onError);

    /**
     * 返回当前 scope 可用的工具列表。
     *
     * @param scope 聊天 scope
     * @return 工具列表
     */
    default List<AiTool> listTools(ChatScope scope) {
        return List.of();
    }

    /**
     * 直接调用工具。
     *
     * @param scope    聊天 scope
     * @param toolCall 工具调用
     * @return 工具执行结果
     */
    default AiToolResult callTool(ChatScope scope, AiToolCall toolCall) {
        return AiToolResult.builder()
                .callId(toolCall == null ? null : toolCall.getId())
                .success(false)
                .error("当前聊天客户端未实现工具直调")
                .build();
    }

    /**
     * 仅使用输入文本执行聊天。
     *
     * @param input 输入文本
     * @return 文本响应
     */
    default String chatSync(String input) {
        return chat(ChatScope.of(input)).getText();
    }

    /**
     * 使用输入文本和上下文执行聊天。
     *
     * @param input   输入文本
     * @param context 上下文
     * @return 文本响应
     */
    default String chat(String input, ChatContext context) {
        return chat(ChatScope.builder()
                .input(input)
                .context(context)
                .build()).getText();
    }

    /**
     * 仅消费流式输出。
     *
     * @param scope    聊天 scope
     * @param consumer 输出消费者
     */
    default void chat(ChatScope scope, Consumer<String> consumer) {
        chat(scope, consumer, () -> {
        }, throwable -> {
            throw new RuntimeException(throwable);
        });
    }

    @Override
    default void close() {
    }
}
