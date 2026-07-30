package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_global_setting")
public class SysAiGlobalSetting {

    @TableId(type = IdType.AUTO)
    private Long sysAiGlobalSettingId;

    @TableField("group_name")
    private String sysAiGlobalSettingGroup;

    @TableField("field_name")
    private String sysAiGlobalSettingField;

    @TableField("field_value")
    private String sysAiGlobalSettingValue;

    @TableField("value_type")
    private String sysAiGlobalSettingValueType;

    @TableField("description")
    private String sysAiGlobalSettingDescription;

    @TableField("sort")
    private Integer sysAiGlobalSettingSort;

    @TableField("status")
    private Integer sysAiGlobalSettingStatus;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
