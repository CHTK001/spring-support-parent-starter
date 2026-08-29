package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话消息实体（对齐前端 {@code StoredMessageResponse}），同时作为 MyBatis-Plus ORM 实体。
 *
 * <p>消息主键为 {@code messageId}（UUIDv7 字符串）；{@code msgId} 为历史字段，
 * 与 {@code messageId} 保持一致。持久化到 {@code sys_ai_conversation_message} 表。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_conversation_message_} 作为前缀，实体字段保持简短语义名，
 * 通过 {@link TableField} 显式映射。JSON 字段说明：{@code content} 支持文本或结构化对象，
 * 通过 {@link JsonOrPlainTypeHandler} 落库 —— 纯文本按原样存储，结构化对象按 JSON 存储，
 * 从而保证普通消息前端展示不带多余引号。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_conversation_message", autoResultMap = true)
public class SysAiConversationMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息主键（UUIDv7 字符串），由后端 {@code newId()} 生成并填充
     */
    @TableId(value = "sys_ai_conversation_message_id", type = IdType.INPUT)
    private String messageId;

    /**
     * 所属会话 ID
     */
    @TableField("sys_ai_conversation_message_conversation_id")
    private String conversationId;

    /**
     * 历史字段，与 messageId 一致
     */
    @TableField("sys_ai_conversation_message_msg_id")
    private String msgId;

    /**
     * 消息类型：text|tips|tool_call|tool_group|agent_status|permission|plan|thinking|available_commands
     */
    @TableField("sys_ai_conversation_message_type")
    private String type = "text";

    /**
     * 消息角色：user | assistant（仅内部用于拼装 LLM 历史，不向前端暴露）
     */
    @TableField("sys_ai_conversation_message_role")
    private String role;

    /**
     * 消息内容（文本或结构化对象），采用「纯文本或 JSON」双模式存储
     */
    @TableField(value = "sys_ai_conversation_message_content", typeHandler = JsonOrPlainTypeHandler.class)
    private Object content;

    /**
     * 展示位置：left|right|center|pop
     */
    @TableField("sys_ai_conversation_message_position")
    private String position;

    /**
     * 消息状态：finish|pending|error|work
     */
    @TableField("sys_ai_conversation_message_status")
    private String status = "finish";

    /**
     * 是否隐藏
     */
    @TableField("sys_ai_conversation_message_hidden")
    private boolean hidden = false;

    /**
     * 创建时间（epoch 毫秒）
     */
    @TableField("sys_ai_conversation_message_created_at")
    private Long createdAt;
}