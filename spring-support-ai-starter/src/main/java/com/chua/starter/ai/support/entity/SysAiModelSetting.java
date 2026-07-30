package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_model_setting")
public class SysAiModelSetting {

    @TableId(type = IdType.AUTO)
    private Integer sysAiModelSettingId;

    @TableField("module_id")
    private Integer sysAiModelSettingModuleId;

    @TableField("model_code")
    private String sysAiModelSettingCode;

    @TableField("model_name")
    private String sysAiModelSettingName;

    @TableField("model_provider")
    private String sysAiModelSettingProvider;

    @TableField("model_value")
    private String sysAiModelSettingValue;

    @TableField("status")
    private Integer sysAiModelSettingStatus;

    @TableField("sort")
    private Integer sysAiModelSettingSort;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
