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
    @TableId(value = "sys_ai_template_id", type = IdType.AUTO)
    /** SYSAI模板ID */
    private Integer id;

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
    private String type;

    /**
     * 模板分类
     */
    @TableField("sys_ai_template_category")
    private String category;

    /**
     * 模板名称
     */
    @TableField("sys_ai_template_name")
    private String name;

    /**
     * 模板备注
     */
    @TableField("sys_ai_template_remark")
    private String remark;

    /**
     * 模板内容（推荐提示词）
     */
    @TableField("sys_ai_template_recommend_prompt")
    private String content;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_template_status")
    private Integer status;

    /**
     * 排序值
     */
    @TableField("sys_ai_template_sort")
    private Integer sort;

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

