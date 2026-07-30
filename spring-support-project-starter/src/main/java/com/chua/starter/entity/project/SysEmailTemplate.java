package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

@Data
@TableName("sys_email_template")
public class SysEmailTemplate extends SysBase {

    @TableId(value = "sys_email_template_id", type = IdType.AUTO)
    private Integer sysEmailTemplateId;
    private Integer sysProjectId;
    private Integer sysProjectType;
    private Integer sysEmailTemplateSort;
    private Integer sysEmailTemplateCategory;
    private String sysEmailTemplateName;
    private String sysEmailTemplateCallbackUrl;
    private String sysEmailTemplateCode;
    private String sysEmailTemplateContent;
    private Integer sysEmailTemplateDelete;
    private Integer sysEmailTemplateStatus;
    private Integer sysEmailTemplateType;
    private Integer sysEmailTemplateDefault;
    private Integer sysEmailTemplateApprove;
    private String sysEmailTemplateApproveReject;
    private Integer sysEmailTemplateApproveCreateBy;
    private Integer sysEmailTemplateDisabled;
    private String sysEmailTemplateOrigin;
    private String sysEmailTemplateOriginUuid;
}
