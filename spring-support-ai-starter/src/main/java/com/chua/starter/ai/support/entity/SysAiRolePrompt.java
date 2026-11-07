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
 * AI 角色分形态提示词实体，对应表 {@code sys_ai_role_prompt}。
 *
 * <p>一条记录 = 一个角色在一种形态（build / plan）下的系统提示词。
 * {@link com.chua.starter.ai.support.entity.SysAiRole#getSystemPrompt()} 是历史单条提示词，
 * 本表用于承载 build / plan 的差异化配置；本表未命中时回退到那一列，历史角色行为不变。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("sys_ai_role_prompt")
public class SysAiRolePrompt {

    /**
     * 主键标识
     */
    @TableId(value = "sys_ai_role_prompt_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 归属用户标识（多用户隔离），与 {@link SysAiRole#getUserId()} 一致
     */
    @TableField("user_id")
    private String userId;

    /**
     * 角色主键，指向 sys_ai_role.sys_ai_role_id
     */
    @TableField("role_id")
    private Integer roleId;

    /**
     * 提示词适用形态：build（直接执行）/ plan（先规划后执行）
     */
    @TableField("prompt_mode")
    private String promptMode;

    /**
     * 该形态下的系统提示词正文
     */
    @TableField("sys_ai_role_prompt_content")
    private String content;

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_role_prompt_status")
    private Integer status;

    /**
     * 逻辑删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_role_prompt_delete")
    @TableLogic
    private Integer deleted;

    /**
     * 创建名称
     */
    @TableField(fill = FieldFill.INSERT)
    private String createName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}