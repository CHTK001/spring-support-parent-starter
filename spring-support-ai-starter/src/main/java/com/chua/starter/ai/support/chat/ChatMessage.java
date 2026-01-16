package com.chua.starter.ai.support.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 角色：user或assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}

