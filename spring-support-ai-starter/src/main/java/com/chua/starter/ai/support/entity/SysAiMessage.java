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
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_message")
public class SysAiMessage {

    /**
     * 消息主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiMessageId;

    /**
     * 会话 ID
     */
    @TableField("sys_ai_session_id")
    private Long sysAiSessionId;

    /**
     * 消息角色（system / user / assistant / tool）
     */
    @TableField("sys_ai_message_role")
    private String sysAiMessageRole;

    /**
     * 消息类型（TEXT 等）
     */
    @TableField("sys_ai_message_type")
    private String sysAiMessageType;

    /**
     * 消息内容
     */
    @TableField("sys_ai_message_content")
    private String sysAiMessageContent;

    /**
     * 附件地址
     */
    @TableField("sys_ai_message_attachment")
    private String sysAiMessageAttachment;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
