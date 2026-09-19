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
     * 模板主键 标识
     */
    @TableId(value = "sys_ai_template_id", type = IdType.AUTO)
    /** SYSAI模板标识 */
    private Integer id;

    /**
    * 所属模块 标识
    */
    @TableField("sys_ai_module_id")
    /** sysaimoduleid */
    private Integer sysAiModuleId;

    /**
    * 模板类型
    */
    @TableField("sys_ai_template_type")
    private String type; // 类型

    /**
     * 模板分类
     */
    @TableField("sys_ai_template_category")
    private String category; // 分类

    /**
     * 模板名称
     */
    @TableField("sys_ai_template_name")
    private String name; // 名称

    /**
     * 模板备注
     */
    @TableField("sys_ai_template_remark")
    private String remark; // 评论

    /**
     * 模板内容（推荐提示词）
     */
    @TableField("sys_ai_template_recommend_prompt")
    private String content; // 内容

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_template_status")
    private Integer status; // 状态

    /**
     * 排序值
     */
    @TableField("sys_ai_template_sort")
    private Integer sort; // 排序

    /**
     * 创建时间
     */
    @TableField("create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
    * 更新时间
    */
    @TableField("update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;
}

