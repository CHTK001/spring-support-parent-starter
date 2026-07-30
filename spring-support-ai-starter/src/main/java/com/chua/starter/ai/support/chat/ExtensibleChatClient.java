package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;

import java.util.List;

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
