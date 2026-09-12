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
    * 模型设置主键 标识
     */
    @TableId(value = "sys_ai_model_setting_id", type = IdType.AUTO)
    /** SYSAI模型设置标识 */
    private Integer id;

    /**
    * 所属模块 标识
     */
    @TableField("module_id")
    private Integer moduleId; // moduleid

    /**
    * 模型编码
     */
    @TableField("model_code")
    private String code; // 编码

    /**
    * 模型名称
     */
    @TableField("model_name")
    private String name; // 名称

    /**
    * 模型厂商
     */
    @TableField("model_provider")
    private String provider; // 提供者

    /**
    * 模型值（如 baseurl / 模型 标识）
     */
    @TableField("model_value")
    private String value; // 价值观

    /**
    * 状态：1 启用，0 停用
     */
    @TableField("status")
    private Integer status; // 状态

    /**
    * 排序值
     */
    @TableField("sort")
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

