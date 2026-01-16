package com.chua.starter.ai.support.chat;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天上下文信息
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
public class ChatContext {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 历史对话记录
     */
    private java.util.List<ChatMessage> history;

    /**
     * 扩展属性
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 添加扩展属性
     *
     * @param key   键
     * @param value 值
     * @return 当前上下文
     */
    public ChatContext attribute(String key, Object value) {
        this.attributes.put(key, value);
        return this;
    }

    /**
     * 获取扩展属性
     *
     * @param key 键
     * @return 值
     */
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }
}

