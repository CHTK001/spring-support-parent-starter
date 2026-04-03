package com.chua.starter.ai.support.configuration;

import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.service.AiService;
import com.chua.starter.ai.support.service.AsyncAiService;
import com.chua.starter.ai.support.service.ReactiveAiService;

/**
 * {@link AiChat} 工厂。
 *
 * @author CH
 * @since 2026/04/03
 */
public class AiChatFactory {

    private final AiService aiService;
    private final AsyncAiService asyncAiService;
    private final ReactiveAiService reactiveAiService;

    /**
     * 创建工厂实例。
     *
     * @param aiService         同步 AI 服务
     * @param asyncAiService    异步 AI 服务
     * @param reactiveAiService 响应式 AI 服务
     */
    public AiChatFactory(AiService aiService, AsyncAiService asyncAiService, ReactiveAiService reactiveAiService) {
        this.aiService = aiService;
        this.asyncAiService = asyncAiService;
        this.reactiveAiService = reactiveAiService;
    }

    /**
     * 创建同步加异步能力齐全的 {@link AiChat}。
     *
     * @return AiChat 实例
     */
    public AiChat create() {
        return AiChat.of(aiService, asyncAiService, reactiveAiService);
    }

    /**
     * 仅创建同步版 {@link AiChat}。
     *
     * @return AiChat 实例
     */
    public AiChat createSync() {
        return AiChat.of(aiService);
    }

    /**
     * 返回同步 AI 服务。
     *
     * @return AI 服务
     */
    public AiService getAiService() {
        return aiService;
    }

    /**
     * 返回异步 AI 服务。
     *
     * @return 异步 AI 服务
     */
    public AsyncAiService getAsyncAiService() {
        return asyncAiService;
    }

    /**
     * 返回响应式 AI 服务。
     *
     * @return 响应式 AI 服务
     */
    public ReactiveAiService getReactiveAiService() {
        return reactiveAiService;
    }
}
