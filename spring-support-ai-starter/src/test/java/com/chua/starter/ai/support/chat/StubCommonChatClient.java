package com.chua.starter.ai.support.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatClient 测试用 common 客户端桩。
 */
final class StubCommonChatClient implements com.chua.common.support.ai.ChatClient {

    String factory;
    String model;
    String systemPrompt;
    boolean mcpEnabled;
    final List<String> userHistories = new ArrayList<>();
    final List<String> assistantHistories = new ArrayList<>();
    final List<String> images = new ArrayList<>();

    @Override
    public com.chua.common.support.ai.ChatClient model(String model) {
        this.model = model;
        return this;
    }

    @Override
    public com.chua.common.support.ai.ChatClient system(String system) {
        this.systemPrompt = system;
        return this;
    }

    @Override
    public com.chua.common.support.ai.ChatClient addUserHistory(String content) {
        userHistories.add(content);
        return this;
    }

    @Override
    public com.chua.common.support.ai.ChatClient addAssistantHistory(String content) {
        assistantHistories.add(content);
        return this;
    }

    @Override
    public com.chua.common.support.ai.ChatClient addImage(String imageUrl) {
        images.add(imageUrl);
        return this;
    }

    @Override
    public String chatSync(String prompt) {
        if (prompt.contains("输入优化器")) {
            return "optimized input";
        }
        if (prompt.contains("上下文压缩器")) {
            return "compressed history";
        }
        return (model == null ? "model-a" : model) + ":" + prompt;
    }

    @Override
    public String chatSync(String prompt, long timeout) {
        return chatSync(prompt);
    }
}
