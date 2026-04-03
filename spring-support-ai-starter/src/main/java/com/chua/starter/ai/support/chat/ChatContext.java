package com.chua.starter.ai.support.chat;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天上下文。
 * <p>
 * 该对象只描述一次聊天 scope 内的上下文，不承载 session 生命周期。
 *
 * @author CH
 * @since 2026/04/03
 */
@Data
public class ChatContext {

    /**
     * 当前用户标识。
     */
    private String userId;

    /**
     * 传入当前 scope 的历史消息。
     */
    private List<ChatMessage> history = new ArrayList<>();

    /**
     * 上下文变量。
     */
    private Map<String, Object> variables = new LinkedHashMap<>();

    /**
     * 扩展属性。
     */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * 放入上下文变量。
     *
     * @param key   变量名
     * @param value 变量值
     * @return 当前上下文
     */
    public ChatContext variable(String key, Object value) {
        variables.put(key, value);
        return this;
    }

    /**
     * 放入扩展属性。
     *
     * @param key   属性名
     * @param value 属性值
     * @return 当前上下文
     */
    public ChatContext attribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }
}
