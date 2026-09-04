package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型设置实体，对应表 {@code sys_ai_model_setting}。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_model_setting")
public class SysAiModelSetting {

    /**
     * 模型设置主键 ID
     */
    @TableId(value = "sys_ai_model_setting_id", type = IdType.AUTO)
    /** SYSAI模型设置ID */
    private Integer id;

    /**
     * 所属模块 ID
     */
    @TableField("module_id")
    private Integer moduleId;

    /**
     * 模型编码
     */
    @TableField("model_code")
    private String code;

    /**
     * 模型名称
     */
    @TableField("model_name")
    private String name;

    /**
     * 模型厂商
     */
    @TableField("model_provider")
    private String provider;

    /**
     * 模型值（如 baseUrl / model 标识）
     */
    @TableField("model_value")
    private String value;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("status")
    private Integer status;

    /**
     * 排序值
     */
    @TableField("sort")
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

