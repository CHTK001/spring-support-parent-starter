package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 需求条目实体，持久化到 {@code sys_ai_requirement} 表。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_requirement", autoResultMap = true)
public class SysAiRequirement implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 需求 ID（UUIDv7） */
    @TableId(value = "sys_ai_requirement_id", type = IdType.INPUT)
    private String requirementId;

    /** 展示序号（自增） */
    @TableField("sys_ai_requirement_display_no")
    private Integer displayNo;

    /** 需求标题 */
    @TableField("sys_ai_requirement_title")
    private String title;

    /** 需求内容（Markdown） */
    @TableField("sys_ai_requirement_content")
    private String content;

    /** 标签 */
    @TableField("sys_ai_requirement_tag")
    private String tag;

    /** 执行次序（点分十进制） */
    @TableField("sys_ai_requirement_order_key")
    private String orderKey;

    /** 状态 */
    @TableField("sys_ai_requirement_status")
    private String status;

    /** 完成记录 */
    @TableField("sys_ai_requirement_completion_note")
    private String completionNote;

    /** 来源会话 ID */
    @TableField("sys_ai_requirement_owner_conversation_id")
    private String ownerConversationId;

    /** 来源终端 ID */
    @TableField("sys_ai_requirement_owner_terminal_id")
    private String ownerTerminalId;

    /** 创建来源 */
    @TableField("sys_ai_requirement_origin")
    private String origin;

    /** 开始执行时间戳 */
    @TableField("sys_ai_requirement_started_at")
    private Long startedAt;

    /** 完成时间戳 */
    @TableField("sys_ai_requirement_completed_at")
    private Long completedAt;

    /** 重试次数 */
    @TableField("sys_ai_requirement_attempt_count")
    private Integer attemptCount;

    /** 扩展字段（附件、自定义字段等） */
    @TableField(value = "sys_ai_requirement_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    @TableField("create_name")
    private String createName;

    @TableField("create_by")
    private Integer createBy;

    @TableField("create_time")
    private Long createTime;

    @TableField("update_name")
    private String updateName;

    @TableField("update_by")
    private Integer updateBy;

    @TableField("update_time")
    private Long updateTime;
}
