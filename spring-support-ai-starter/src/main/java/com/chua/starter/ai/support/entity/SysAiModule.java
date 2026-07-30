package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_ai_module")
public class SysAiModule {

    @TableId(type = IdType.AUTO)
    private Integer sysAiModuleId;

    @TableField("module_code")
    private String sysAiModuleCode;

    @TableField("module_name")
    private String sysAiModuleName;

    @TableField("module_type")
    private String sysAiModuleType;

    @TableField("module_vendor")
    private String sysAiModuleVendor;

    @TableField("manufacturers")
    private String sysAiModuleManufacturers;

    @TableField("api_expose")
    private Integer sysAiModuleApiExpose;

    @TableField("status")
    private Integer sysAiModuleStatus;

    @TableField("sort")
    private Integer sysAiModuleSort;

    @TableField("description")
    private String sysAiModuleDescription;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 模块的配置项列表（仅内存使用，不映射数据库字段）。
     */
    @TableField(exist = false)
    private transient List<SysAiSetting> sysAiSettings;
}
