package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_template")
public class SysAiTemplate {

    @TableId(type = IdType.AUTO)
    private Integer sysAiTemplateId;

    @TableField("module_id")
    private Integer sysAiModuleId;

    @TableField("template_type")
    private String sysAiTemplateType;

    @TableField("template_category")
    private String sysAiTemplateCategory;

    @TableField("template_name")
    private String sysAiTemplateName;

    @TableField("template_remark")
    private String sysAiTemplateRemark;

    @TableField("template_content")
    private String sysAiTemplateContent;

    @TableField("status")
    private Integer sysAiTemplateStatus;

    @TableField("sort")
    private Integer sysAiTemplateSort;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
