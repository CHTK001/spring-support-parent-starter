package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_global_setting")
public class SysAiGlobalSetting {

    @TableId(type = IdType.AUTO)
    private Long sysAiGlobalSettingId;

    @TableField("sys_ai_global_setting_group")
    private String sysAiGlobalSettingGroup;

    @TableField("sys_ai_global_setting_field")
    private String sysAiGlobalSettingField;

    @TableField("sys_ai_global_setting_value")
    private String sysAiGlobalSettingValue;

    @TableField("sys_ai_global_setting_value_type")
    private String sysAiGlobalSettingValueType;

    @TableField("sys_ai_global_setting_description")
    private String sysAiGlobalSettingDescription;

    @TableField("sys_ai_global_setting_sort")
    private Integer sysAiGlobalSettingSort;

    @TableField("sys_ai_global_setting_status")
    private Integer sysAiGlobalSettingStatus;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
