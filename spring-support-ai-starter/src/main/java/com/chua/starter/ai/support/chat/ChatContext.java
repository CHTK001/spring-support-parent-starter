package com.chua.starter.ai.support.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* 对话上下文，携带用户、历史记录、变量与扩展属性。
*
* @since 4.0.0.42
* @author CH
 */
public class ChatContext {

    /**
    * 用户 标识
     */
    private String userId;

    /**
    * 历史消息列表
     */
    private List<ChatMessage> history;

    /**
    * 模板变量
     */
    private Map<String, Object> variables;

    /**
    * 扩展属性
     */
    private Map<String, Object> attributes;

    /**
    * 获取用户 标识。
    *
    * @return 用户 标识
     */
    public String getUserId() {
        return userId;
    }

    /**
    * 设置用户 标识。
    *
    * @param userId 用户 标识
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
    * 获取历史消息列表。
    *
    * @return 历史消息列表
     */
    public List<ChatMessage> getHistory() {
        return history;
    }

    /**
    * 设置历史消息列表。
    *
    * @param history 历史消息列表
     */
    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }

    /**
    * 获取模板变量。
    *
    * @return 模板变量
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
    * 设置模板变量。
    *
    * @param variables 模板变量
     */
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    /**
    * 获取扩展属性。
    *
    * @return 扩展属性
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
    * 设置扩展属性。
    *
    * @param attributes 扩展属性
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
    * 写入扩展属性（不存在时自动创建）。
    *
    * @param key   属性键
    * @param value 属性值
     */
    public void attribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>(8);
        }
        attributes.put(key, value);
    }

    /**
    * 读取扩展属性。
    *
    * @param key 属性键
    * @return 属性值，不存在时返回 空
     */
    public Object attribute(String key) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(key);
    }
}
