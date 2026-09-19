package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息实体，对应表 {@code sys_ai_message}。
 *
 * <p>持久化会话下的每一条聊天消息，重启后历史不丢失。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_message")
public class SysAiMessage {

    /**
     * 消息主键 标识
     */
    @TableId(value = "sys_ai_message_id", type = IdType.AUTO)
    /** SYSAI消息标识 */
    private Long id;

    /**
     * 父消息ID（用户消息ID，AI回复的parent_id指向用户消息）
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 会话 标识
     */
    @TableField("sys_ai_session_id")
    /** SYSAI会话标识 */
    private Long sysAiSessionId;

    /**
     * 消息角色（系统 / 用户 / assistant / tool）
     */
    @TableField("sys_ai_message_role")
    private String role; // 角色

    /**
     * 消息类型（文本 等）
     */
    @TableField("sys_ai_message_type")
    private String type; // 类型

    /**
     * 消息内容
     */
    @TableField("sys_ai_message_content")
    private String content; // 内容

    /**
     * 模型名称（AI回复时使用的模型）
     */
    @TableField("model_name")
    private String modelName; // 模型名称

    /**
     * Prompt tokens 数量
     */
    @TableField("prompt_tokens")
    private Integer promptTokens; // Prompt tokens 数量

    /**
     * Completion tokens 数量
     */
    @TableField("completion_tokens")
    private Integer completionTokens; // Completion tokens 数量

    /**
     * Total tokens 数量
     */
    @TableField("total_tokens")
    private Integer totalTokens; // Total tokens 数量

    /**
     * 附件地址
     */
    @TableField("sys_ai_message_attachment")
    private String attachment; // attachment

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;
}
