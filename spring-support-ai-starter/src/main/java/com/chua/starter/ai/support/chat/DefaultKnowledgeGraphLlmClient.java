package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatResponse.State;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultKnowledgeGraphLlmClient implements KnowledgeGraphLlmClient {

    private final KnowledgeGraphSettings settings;
    private final ChatClient chatClient;

    public DefaultKnowledgeGraphLlmClient(KnowledgeGraphSettings settings, ChatClient chatClient) {
        this.settings = settings;
        this.chatClient = chatClient;
    }

    @Override
    public String chat(String input) {
        if (chatClient == null) {
            return null;
        }
        try {
            String text = chatClient.chatSync(input);
            return text;
        } catch (Exception e) {
            log.warn("[DefaultKnowledgeGraphLlmClient] 调用失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        if (chatClient != null) {
            try {
                chatClient.close();
            } catch (Exception ignored) {
            }
        }
    }
}
