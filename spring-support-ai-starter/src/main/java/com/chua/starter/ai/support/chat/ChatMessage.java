package com.chua.starter.ai.support.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条聊天消息。
 *
 * @author CH
 * @since 2026/04/03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 消息角色。
     * 常见取值为 {@code user}、{@code assistant}。
     */
    private String role;

    /**
     * 消息文本内容。
     */
    private String content;

    /**
     * 创建用户消息。
     *
     * @param content 消息内容
     * @return 用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * 创建助手消息。
     *
     * @param content 消息内容
     * @return 助手消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
