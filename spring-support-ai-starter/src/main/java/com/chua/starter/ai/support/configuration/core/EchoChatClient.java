package com.chua.starter.ai.support.configuration.core;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;

import java.util.function.Consumer;

/**
* 内置 Echo 对话客户端 —— 无外部 AI 服务时的本地回显客户端。
*
* <p>用于验证会话全链路（POST → WS 流式推送 → turn.completed）而无需真实模型，
* 也会在多轮历史后追加一句说明。仅作本地联调占位，生产请替换为真实提供商。</p>
*
* @since 4.0.0.42
* @author CH
 */
public class EchoChatClient implements ChatClient {

    @Override
    public String chatSync(String prompt) {
        return "[Echo] " + (prompt == null ? "" : prompt);
    }

    @Override
    public ChatSyncResponse chatSyncWithResponse(String prompt) {
        return ChatSyncResponse.builder()
                .text(chatSync(prompt))
                .build();
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer,
                     Runnable onComplete, Consumer<Throwable> onError) {
        try {
            String text = chatSync(prompt);
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.STREAMING)
                    .content(text)
                    .build());
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.STOP)
                    .build());
            onComplete.run();
        } catch (Throwable e) {
            onError.accept(e);
        }
    }
}
