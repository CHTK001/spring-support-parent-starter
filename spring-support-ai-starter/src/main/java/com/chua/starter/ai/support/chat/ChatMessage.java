package com.chua.starter.ai.support.chat;

/**
* 对话消息。
*
* @since 4.0.0.42
* @author CH
 */
public class ChatMessage {

    /**
    * 消息 标识
    */
    private Long id;

    /**
    * 所属对话 标识
    */
    private Long dialogId;

    /**
    * 消息时间戳（毫秒）
    */
    private Long timestamp;

    /**
    * 角色（系统 / 用户 / assistant / tool）
    */
    private String role;

    /**
    * 消息内容
    */
    private String content;

    /**
    * 空参构造。
    */
    public ChatMessage() {
    }

    /**
    * 简易构造，时间戳取当前系统时间。
    *
    * @param role    角色
    * @param content 消息内容
    */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    /**
    * 全参构造，时间戳取当前系统时间。
    *
    * @param id       消息 标识
    * @param dialogId 对话 标识
    * @param role     角色
    * @param content  消息内容
    */
    public ChatMessage(Long id, Long dialogId, String role, String content) {
        this.id = id;
        this.dialogId = dialogId;
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    /**
    * 获取消息 标识。
    *
    * @return 消息 标识
    */
    public Long getId() {
        return id;
    }

    /**
    * 设置消息 标识。
    *
    * @param id 消息 标识
    */
    public void setId(Long id) {
        this.id = id;
    }

    /**
    * 获取对话 标识。
    *
    * @return 对话 标识
    */
    public Long getDialogId() {
        return dialogId;
    }

    /**
    * 设置对话 标识。
    *
    * @param dialogId 对话 标识
    */
    public void setDialogId(Long dialogId) {
        this.dialogId = dialogId;
    }

    /**
    * 获取消息时间戳。
    *
    * @return 消息时间戳（毫秒）
    */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
    * 设置消息时间戳。
    *
    * @param timestamp 消息时间戳（毫秒）
    */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
    * 获取角色。
    *
    * @return 角色
    */
    public String getRole() {
        return role;
    }

    /**
    * 设置角色。
    *
    * @param role 角色
    */
    public void setRole(String role) {
        this.role = role;
    }

    /**
    * 获取消息内容。
    *
    * @return 消息内容
    */
    public String getContent() {
        return content;
    }

    /**
    * 设置消息内容。
    *
    * @param content 消息内容
    */
    public void setContent(String content) {
        this.content = content;
    }

    /**
    * 创建指定角色与内容的消息。
    *
    * @param role    角色
    * @param content 消息内容
    * @return 消息实例
    */
    public static ChatMessage of(String role, String content) {
        /**
        * 对话消息。
        * @param role 角色
        * @param content 内容
        */
        return new ChatMessage(role, content);
    }

    /**
    * 创建助手消息。
    *
    * @param content 消息内容
    * @return 消息实例
    */
    public static ChatMessage assistant(String content) {
        /**
        * 对话消息。
        * @param "assistant" "assistant"
        * @param content 内容
        */
        return new ChatMessage("assistant", content);
    }
}
