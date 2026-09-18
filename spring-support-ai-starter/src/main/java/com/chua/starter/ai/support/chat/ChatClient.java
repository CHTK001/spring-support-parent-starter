package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ModelDefinition;

import java.util.List;

/**
* 全局 对话客户端 接口，封装厂商无关的对话、工具调用与模型管理能力。
*
* <p>支持对象池: 通过 {@code pool(Number)} 调整池大小, 默认单例模式 (即 {@code pool(1)})。
* 游泳池 能力继承自 {@link com.chua.common.support.pool.PooledObjectClient}。
*
* @since 4.0.0.42
* @author CH
 */
public interface ChatClient extends com.chua.common.support.ai.chat.ChatClient {

    /**
    * 对话。
    * @param scope scope
    * @return 对话的结果
    */
    ChatResponse chat(ChatScope scope);

    /**
    * 对话。
    * @param scope scope
    * @param consumer consumer
    * @param onComplete on完成
    * @param onError on错误
    */
    void chat(ChatScope scope, java.util.function.Consumer<String> consumer, Runnable onComplete, java.util.function.Consumer<Throwable> onError);

    /**
    * 列表tools。
    * @param scope scope
    * @return 列表tools的结果
    */
    java.util.List<AiTool> listTools(ChatScope scope);

    /**
    * calltool。
    * @param scope scope
    * @param toolCall toolcall
    * @return callTool的结果
    */
    AiToolResult callTool(ChatScope scope, AiToolCall toolCall);

    /**
    * 获取模型名称。
    * @return 获取模型名称的结果
    */
    String getModelName();

    /**
    * 获取工厂。
    * @return 获取工厂的结果
    */
    String getFactory();

    /**
    * 获取提供者。
    * @return 获取提供者的结果
    */
    String getProvider();

    /**
    * 获取默认模型。
    * @return 获取默认模型的结果
    */
    String getDefaultModel();

    /**
    * 获取端点。
    * @return 获取端点的结果
    */
    String getEndpoint();

    /**
    * 获取模型定义列表（与父接口的 {@code listModels() -> List<String>} 重载共存, 这里返回更详细的定义信息）。
    *
    * @return 模型定义列表
    */
    default List<ModelDefinition> listModelDefinitions() {
        return List.of();
    }

    /**
    * 工厂接口。
    *
    * @author CH
    * @since 4.0.0
    */
    interface Factory {
        /**
        * 创建。
        * @param provider 提供者
        * @param setting setting
        * @return 创建的结果
        */
        ChatClient create(String provider, ChatClientSetting setting);
    }
}
