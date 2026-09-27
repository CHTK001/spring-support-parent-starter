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
     * 会话主键 标识
     */
    @TableId(value = "sys_ai_session_id", type = IdType.AUTO)
    /**
     * SYSAI会话标识
    */
    private Long id;

    /**
     * 会话标题
     */
    @TableField("sys_ai_session_title")
    private String title; // title

    /**
     * 会话类型（对话 等）
     */
    @TableField("sys_ai_session_type")
    private String type; // 类型

    /**
     * 关联模块编码
     */
    @TableField("sys_ai_module_code")
    /**
     * sysaimodule代码
    */
    private String sysAiModuleCode;

    /**
     * 会话默认模型ID
     */
    @TableField("model_id")
    private String modelId;

    /**
     * 绑定角色（助手）ID，对应 sys_ai_role 主键；为空表示不使用特定角色
     */
    @TableField("character_id")
    private Long characterId;

    /**
     * 会话级 MCP 服务器ID列表（逗号分隔，仅 v-admin 管理员可配置）；
     * 非空时本次会话仅连接所选 MCP 服务器。
     */
    @TableField("mcp_server_ids")
    private String mcpServerIds;

    /**
     * 会话级设置 JSON（运行模式 build/plan、温度、topP、知识库引用等），
     * 由前端 settings 对象整体序列化持久化；为空表示使用默认设置。
     */
    @TableField("settings")
    private String settings;

    /**
     * 关联用户 标识
     */
    @TableField("sys_user_id")
    /**
     * SYS用户标识
    */
    private String sysUserId;

    /**
     * 会话状态
     */
    @TableField("sys_ai_session_status")
    private Integer status; // 状态

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;

    @TableField("is_archived")
    /**
     * 是否已归档
    */
    private Boolean isArchived;
}

