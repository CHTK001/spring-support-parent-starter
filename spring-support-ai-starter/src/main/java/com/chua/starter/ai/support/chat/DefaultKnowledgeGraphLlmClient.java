package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatResponse.State;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认知识图谱 LLM 客户端。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DefaultKnowledgeGraphLlmClient implements KnowledgeGraphLlmClient {

    /**
     * 知识图谱 LLM 设置
     */
    private final KnowledgeGraphSettings settings;

    /**
     * 底层 ChatClient
     */
    private final ChatClient chatClient;

    /**
     * 全参构造。
     *
     * @param settings   知识图谱 LLM 设置
     * @param chatClient 底层 ChatClient
     */
    public DefaultKnowledgeGraphLlmClient(KnowledgeGraphSettings settings, ChatClient chatClient) {
        this.settings = settings;
        this.chatClient = chatClient;
    }

    @Override
    /** Chat */
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
    /** 关闭 */
    public void close() {
        if (chatClient != null) {
            try {
                chatClient.close();
            } catch (Exception ignored) {
            }
        }
    }
}
