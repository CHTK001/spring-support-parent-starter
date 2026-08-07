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
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_setting")
public class SysAiSetting {

    /**
     * 设置主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Integer sysAiSettingId;

    /**
     * 所属模块 ID
     */
    @TableField("sys_ai_module_id")
    private Integer sysAiSettingModuleId;

    /**
     * 设置字段名（非持久化，逻辑上等于 settingKey）
     */
    @TableField(exist = false)
    @JsonIgnore
    private String sysAiSettingField;

    /**
     * 设置键（持久化到 sys_ai_setting_field 列）
     */
    @TableField("sys_ai_setting_field")
    private String sysAiSettingKey;

    /**
     * 设置值
     */
    @TableField("sys_ai_setting_value")
    private String sysAiSettingValue;

    /**
     * 值类型（STRING / NUMBER / BOOLEAN）
     */
    @TableField("sys_ai_setting_value_type")
    private String sysAiSettingValueType;

    /**
     * 设置描述
     */
    @TableField("sys_ai_setting_description")
    private String sysAiSettingDescription;

    /**
     * 排序值
     */
    @TableField("sys_ai_setting_sort")
    private Integer sysAiSettingSort;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_setting_status")
    private Integer sysAiSettingStatus;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
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

