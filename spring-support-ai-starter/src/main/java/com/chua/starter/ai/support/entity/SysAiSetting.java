package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 设置实体，对应表 {@code sys_ai_setting}。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_setting")
public class SysAiSetting {

    /**
     * 设置主键 ID
     */
    @TableId(type = IdType.AUTO)
    /** SYSAI设置ID */
    private Integer sysAiSettingId;

    /**
     * 所属模块 ID
     */
    @TableField("sys_ai_module_id")
    /** SYSAI设置moduleID */
    private Integer sysAiSettingModuleId;

    /**
     * 设置字段名（非持久化，逻辑上等于 settingKey）
     */
    @TableField(exist = false)
    @JsonIgnore
    /** SYSAI设置field */
    private String sysAiSettingField;

    /**
     * 设置键（持久化到 sys_ai_setting_field 列）
     */
    @TableField("sys_ai_setting_field")
    /** SYSAI设置密钥 */
    private String sysAiSettingKey;

    /**
     * 设置值
     */
    @TableField("sys_ai_setting_value")
    /** SYSAI设置值 */
    private String sysAiSettingValue;

    /**
     * 值类型（STRING / NUMBER / BOOLEAN）
     */
    @TableField("sys_ai_setting_value_type")
    /** SYSAI设置值类型 */
    private String sysAiSettingValueType;

    /**
     * 设置描述
     */
    @TableField("sys_ai_setting_description")
    /** SYSAI设置描述 */
    private String sysAiSettingDescription;

    /**
     * 排序值
     */
    @TableField("sys_ai_setting_sort")
    /** SYSAI设置sort */
    private Integer sysAiSettingSort;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_setting_status")
    /** SYSAI设置状态 */
    private Integer sysAiSettingStatus;

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

    /**
     * 获取设置字段名，未赋值时回退为设置键。
     *
     * @return 设置字段名
     */
    @JsonIgnore
    public String getSysAiSettingField() {
        if (sysAiSettingField != null) {
            return sysAiSettingField;
        }
        return sysAiSettingKey;
    }

    /**
     * 设置设置字段名。
     *
     * @param field 设置字段名
     */
    public void setSysAiSettingField(String field) {
        this.sysAiSettingField = field;
    }
}

