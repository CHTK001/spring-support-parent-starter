package com.chua.starter.ai.support.nomifun.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话消息实体（对齐前端 {@code StoredMessageResponse}），同时作为 MyBatis-Plus ORM 实体。
 *
 * <p>消息主键为 {@code messageId}（UUIDv7 字符串）；{@code msgId} 为历史字段，
 * 与 {@code messageId} 保持一致。持久化到 {@code nomifun_message} 表。</p>
 *
 * <p>JSON 字段说明：{@code content} 支持文本或结构化对象，通过
 * {@link JacksonTypeHandler} 序列化为 TEXT 存储。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("nomifun_message")
public class StoredMessage implements Serializable {

    /**
     * 消息主键（UUIDv7 字符串），由后端 {@code newId()} 生成并填充
     */
    @TableId(value = "message_id", type = IdType.INPUT)
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
     * 消息内容（文本或结构化对象），JSON 序列化存储
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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