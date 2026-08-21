package com.chua.starter.ai.support.nomifun.store;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话消息实体（对齐前端 {@code StoredMessageResponse}）。
 *
 * <p>消息主键为 {@code messageId}（UUIDv7 字符串）；{@code msgId} 为历史字段，
 * 与 {@code messageId} 保持一致。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class StoredMessage implements Serializable {

    /**
     * 消息主键（UUIDv7 字符串）
     */
    private String messageId;

    /**
     * 所属会话 ID
     */
    private String conversationId;

    /**
     * 历史字段，与 messageId 一致
     */
    private String msgId;

    /**
     * 消息类型：text|tips|tool_call|tool_group|agent_status|permission|plan|thinking|available_commands
     */
    private String type = "text";

    /**
     * 消息角色：user | assistant（仅内部用于拼装 LLM 历史，不向前端暴露）
     */
    private String role;

    /**
     * 消息内容（文本或结构化对象）
     */
    private Object content;

    /**
     * 展示位置：left|right|center|pop
     */
    private String position;

    /**
     * 消息状态：finish|pending|error|work
     */
    private String status = "finish";

    /**
     * 是否隐藏
     */
    private boolean hidden = false;

    /**
     * 创建时间（epoch 毫秒）
     */
    private Long createdAt;
}
