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

    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 需求 标识（uuidv7） */
    @TableId(value = "sys_ai_requirement_id", type = IdType.INPUT)
    private String requirementId; // requirementid

    /** 展示序号（自增） */
    @TableField("sys_ai_requirement_display_no")
    private Integer displayNo; // displayno

    /** 需求标题 */
    @TableField("sys_ai_requirement_title")
    private String title; // title

    /** 需求内容（Markdown） */
    @TableField("sys_ai_requirement_content")
    private String content; // 内容

    /** 标签 */
    @TableField("sys_ai_requirement_tag")
    private String tag; // 标签

    /** 执行次序（点分十进制） */
    @TableField("sys_ai_requirement_order_key")
    private String orderKey; // 订单钥匙

    /** 状态 */
    @TableField("sys_ai_requirement_status")
    private String status; // 状态

    /** 完成记录 */
    @TableField("sys_ai_requirement_completion_note")
    private String completionNote; // 完成笔记

    /** 来源会话 标识 */
    @TableField("sys_ai_requirement_owner_conversation_id")
    private String ownerConversationId; // ownerconversationid

    /** 来源终端 标识 */
    @TableField("sys_ai_requirement_owner_terminal_id")
    private String ownerTerminalId; // ownerterminalid

    /** 创建来源 */
    @TableField("sys_ai_requirement_origin")
    private String origin; // origin

    /** 开始执行时间戳 */
    @TableField("sys_ai_requirement_started_at")
    private Long startedAt; // 启动at

    /** 完成时间戳 */
    @TableField("sys_ai_requirement_completed_at")
    private Long completedAt; // 完成at

    /** 重试次数 */
    @TableField("sys_ai_requirement_attempt_count")
    private Integer attemptCount; // 尝试数量

    /** 扩展字段（附件、自定义字段等） */
    @TableField(value = "sys_ai_requirement_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra; // extra

    @TableField("create_name")
    private String createName; // 创建名称

    @TableField("create_by")
    private Integer createBy; // 创建by

    @TableField("create_time")
    private Long createTime; // 创建时间

    @TableField("update_name")
    private String updateName; // 更新名称

    @TableField("update_by")
    private Integer updateBy; // 更新by

    @TableField("update_time")
    private Long updateTime; // 更新时间
}
