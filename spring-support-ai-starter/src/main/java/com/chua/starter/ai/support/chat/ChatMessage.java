package com.chua.starter.ai.support.chat;

public class ChatMessage {

    private Long id;
    private Long dialogId;
    private Long timestamp;
    private String role;
    private String content;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(Long id, Long dialogId, String role, String content) {
        this.id = id;
        this.dialogId = dialogId;
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDialogId() { return dialogId; }
    public void setDialogId(Long dialogId) { this.dialogId = dialogId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public static ChatMessage of(String role, String content) {
        return new ChatMessage(role, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
