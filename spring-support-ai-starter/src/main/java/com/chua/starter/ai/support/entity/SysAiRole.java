package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 角色（人设）实体，对应表 {@code sys_ai_role}。
 *
 * <p>角色是一段预设的系统提示词人物设定，让 AI 以特定身份（如 Java 开发、
 * UI 设计师、营销专家）应答。角色可绑定默认模型与退出参数。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_role")
public class SysAiRole {

    /**
     * 角色主键 标识
     */
    @TableId(value = "sys_ai_role_id", type = IdType.AUTO)
    /** sysairoleid */
    private Integer id;

    /**
    * 角色名称（如 Java 开发助理）
    */
    @TableField("sys_ai_role_name")
    private String name; // 名称

    /**
     * 角色编码（唯一标识，如 Java-dev）
     */
    @TableField("sys_ai_role_code")
    private String code; // 编码

    /**
     * 角色描述
     */
    @TableField("sys_ai_role_description")
    private String description; // description

    /**
     * 系统提示词（人设核心，注入到对话 系统 中）
     */
    @TableField("sys_ai_role_system_prompt")
    private String systemPrompt; // 系统提示符

    /**
     * 绑定默认模型（可空，为空时使用全局默认模型）
     */
    @TableField("sys_ai_role_model")
    private String model; // 模型

    /**
     * 采样温度（可空，为空时使用默认温度）
     */
    @TableField("sys_ai_role_temperature")
    private Double temperature; // temperature

    /**
     * 排序值
     */
    @TableField("sys_ai_role_sort")
    private Integer sort; // 排序

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_role_status")
    private Integer status; // 状态

    /**
     * 逻辑删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_role_delete")
    @TableLogic
    private Integer deleted; // 删除

    /**
     * 创建人名称
     */
    @TableField(fill = FieldFill.INSERT)
    /** 创建名称 */
    private String createName;

    /**
    * 创建时间
    */
    @TableField(fill = FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
    * 更新时间
    */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;
}
