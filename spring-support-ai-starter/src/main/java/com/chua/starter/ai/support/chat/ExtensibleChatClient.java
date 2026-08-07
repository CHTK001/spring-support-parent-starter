package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;

import java.util.List;

/**
 * 可扩展 ChatClient 接口，支持链式配置模型、系统提示词与历史记录。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ExtensibleChatClient extends ChatClient {

    ChatClient model(String model);

    ChatClient system(String system);

    ChatClient temperature(Double temperature);

    ChatClient maxTokens(Integer maxTokens);

    ChatClient addAssistantHistory(String history);

    ChatClient addUserHistory(String history);

    ChatClient addImage(String imageUrl);

    List<String> getImageUrls();

    Double getTemperature();

    Integer getMaxTokens();
}
