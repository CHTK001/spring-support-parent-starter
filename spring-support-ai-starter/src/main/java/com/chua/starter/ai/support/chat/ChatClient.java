package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ModelDefinition;

import java.util.List;

/**
 * 全局 ChatClient 接口，封装厂商无关的对话、工具调用与模型管理能力。
 *
 * <p>支持对象池: 通过 {@code pool(Number)} 调整池大小, 默认单例模式 (即 {@code pool(1)})。
 * pool 能力继承自 {@link com.chua.common.support.pool.PooledObjectClient}。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface ChatClient extends com.chua.common.support.ai.chat.ChatClient {

    ChatResponse chat(ChatScope scope);

    void chat(ChatScope scope, java.util.function.Consumer<String> consumer, Runnable onComplete, java.util.function.Consumer<Throwable> onError);

    java.util.List<AiTool> listTools(ChatScope scope);

    AiToolResult callTool(ChatScope scope, AiToolCall toolCall);

    String getModelName();

    String getFactory();

    String getProvider();

    String getDefaultModel();

    String getEndpoint();

    /**
     * 获取模型定义列表（与父接口的 {@code listModels() -> List<String>} 重载共存, 这里返回更详细的定义信息）。
     *
     * @return 模型定义列表
     */
    default List<ModelDefinition> listModelDefinitions() {
        return List.of();
    }

    interface Factory {
        ChatClient create(String provider, ChatClientSetting setting);
    }
}