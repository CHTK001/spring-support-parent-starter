package com.chua.starter.ai.support.mcp;

import com.chua.deeplearning.support.ml.mcp.model.ChatContext;
import com.chua.deeplearning.support.ml.mcp.model.ChatMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatContext适配器
 * <p>
 * 将Spring层的ChatContext适配为通用ChatContext
 *
 * @author CH
 * @since 2025/01/XX
 */
public class ChatContextAdapter {

    /**
     * 将Spring层的ChatContext转换为通用ChatContext
     *
     * @param springContext Spring层的ChatContext
     * @return 通用ChatContext
     */
    public static ChatContext toDeepLearningContext(com.chua.starter.ai.support.chat.ChatContext springContext) {
        if (springContext == null) {
            return new ChatContext();
        }
        ChatContext context = new ChatContext();
        context.setUserId(springContext.getUserId());
        context.setSessionId(springContext.getSessionId());
        if (springContext.getHistory() != null) {
            List<ChatMessage> history = springContext.getHistory().stream()
                    .map(msg -> new ChatMessage(msg.getRole(), msg.getContent()))
                    .collect(Collectors.toList());
            context.setHistory(history);
        }
        if (springContext.getAttributes() != null) {
            context.setAttributes(springContext.getAttributes());
        }
        return context;
    }

    /**
     * 将通用ChatContext转换为Spring层的ChatContext
     *
     * @param deepLearningContext 通用ChatContext
     * @return Spring层的ChatContext
     */
    public static com.chua.starter.ai.support.chat.ChatContext toSpringContext(ChatContext deepLearningContext) {
        if (deepLearningContext == null) {
            return new com.chua.starter.ai.support.chat.ChatContext();
        }
        com.chua.starter.ai.support.chat.ChatContext context = new com.chua.starter.ai.support.chat.ChatContext();
        context.setUserId(deepLearningContext.getUserId());
        context.setSessionId(deepLearningContext.getSessionId());
        if (deepLearningContext.getHistory() != null) {
            List<com.chua.starter.ai.support.chat.ChatMessage> history = deepLearningContext.getHistory().stream()
                    .map(msg -> new com.chua.starter.ai.support.chat.ChatMessage(msg.role(), msg.content()))
                    .collect(Collectors.toList());
            context.setHistory(history);
        }
        if (deepLearningContext.getAttributes() != null) {
            context.setAttributes(deepLearningContext.getAttributes());
        }
        return context;
    }
}

