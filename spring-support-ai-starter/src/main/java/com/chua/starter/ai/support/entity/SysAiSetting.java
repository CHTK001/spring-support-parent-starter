package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_setting")
public class SysAiSetting {

    @TableId(type = IdType.AUTO)
    private Integer sysAiSettingId;

    @TableField("module_id")
    private Integer sysAiSettingModuleId;

    @TableField(exist = false)
    @JsonIgnore
    private String sysAiSettingField;

    @TableField("setting_key")
    private String sysAiSettingKey;

    @TableField("setting_value")
    private String sysAiSettingValue;

    @TableField("value_type")
    private String sysAiSettingValueType;

    @TableField("description")
    private String sysAiSettingDescription;

    @TableField("sort")
    private Integer sysAiSettingSort;

    @TableField("status")
    private Integer sysAiSettingStatus;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableField("field")
    private String sysAiSettingFieldColumn;

    @JsonIgnore
    public String getSysAiSettingField() {
        if (sysAiSettingField != null) {
            return sysAiSettingField;
        }
        return sysAiSettingKey;
    }

    public void setSysAiSettingField(String field) {
        this.sysAiSettingField = field;
    }
}
