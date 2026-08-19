package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模板实体，对应表 {@code sys_ai_template}。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_template")
public class SysAiTemplate {

    /**
     * 模板主键 ID
     */
    @TableId(type = IdType.AUTO)
    /** SYSAI模板ID */
    private Integer sysAiTemplateId;

    /**
     * 所属模块 ID
     */
    @TableField("sys_ai_module_id")
    /** SYSAImoduleID */
    private Integer sysAiModuleId;

    /**
     * 模板类型
     */
    @TableField("sys_ai_template_type")
    /** SYSAI模板类型 */
    private String sysAiTemplateType;

    /**
     * 模板分类
     */
    @TableField("sys_ai_template_category")
    /** SYSAI模板分类 */
    private String sysAiTemplateCategory;

    /**
     * 模板名称
     */
    @TableField("sys_ai_template_name")
    /** SYSAI模板名称 */
    private String sysAiTemplateName;

    /**
     * 模板备注
     */
    @TableField("sys_ai_template_remark")
    /** SYSAI模板remark */
    private String sysAiTemplateRemark;

    /**
     * 模板内容（推荐提示词）
     */
    @TableField("sys_ai_template_recommend_prompt")
    /** SYSAI模板内容 */
    private String sysAiTemplateContent;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_template_status")
    /** SYSAI模板状态 */
    private Integer sysAiTemplateStatus;

    /**
     * 排序值
     */
    @TableField("sys_ai_template_sort")
    /** SYSAI模板sort */
    private Integer sysAiTemplateSort;

    /**
     * 创建时间
     */
    @TableField("create_time")
    /** Create时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    /** Update时间 */
    private LocalDateTime updateTime;
}

