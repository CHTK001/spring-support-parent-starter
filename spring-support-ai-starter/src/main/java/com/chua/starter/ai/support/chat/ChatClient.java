package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ModelDefinition;

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

    default java.util.List<ModelDefinition> listModels() {
        return java.util.List.of();
    }

    interface Factory {
        ChatClient create(String provider, ChatClientSetting setting);
    }
}