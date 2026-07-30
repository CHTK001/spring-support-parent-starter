package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_manufacturer")
public class SysAiManufacturer {

    @TableId(type = IdType.AUTO)
    private Integer sysAiManufacturerId;

    @TableField("manufacturer_code")
    private String sysAiManufacturerCode;

    @TableField("manufacturer_name")
    private String sysAiManufacturerName;

    @TableField("logo_url")
    private String sysAiManufacturerLogoUrl;

    @TableField("description")
    private String sysAiManufacturerDescription;

    @TableField("status")
    private Integer sysAiManufacturerStatus;

    @TableField("sort")
    private Integer sysAiManufacturerSort;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
