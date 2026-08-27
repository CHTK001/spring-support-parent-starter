package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体，对应表 {@code sys_ai_session}。
 *
 * <p>持久化对话会话元信息，配合 {@link SysAiMessage} 实现会话历史不丢失。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_session")
public class SysAiSession {

    /**
     * 会话主键 ID
     */
    @TableId(type = IdType.AUTO)
    /** SYSAI会话ID */
    private Long id;

    /**
     * 会话标题
     */
    @TableField("sys_ai_session_title")
    private String title;

    /**
     * 会话类型（CHAT 等）
     */
    @TableField("sys_ai_session_type")
    private String type;

    /**
     * 关联模块编码
     */
    @TableField("sys_ai_module_code")
    /** SYSAImodule代码 */
    private String sysAiModuleCode;

    /**
     * 关联用户 ID
     */
    @TableField("sys_user_id")
    /** SYS用户ID */
    private String sysUserId;

    /**
     * 会话状态
     */
    @TableField("sys_ai_session_status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    /** Create时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    /** Update时间 */
    private LocalDateTime updateTime;
}
