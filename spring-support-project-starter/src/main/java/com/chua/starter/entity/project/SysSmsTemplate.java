package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

@Data
@TableName("sys_sms_template")
public class SysSmsTemplate extends SysBase {

    @TableId(value = "sys_sms_template_id", type = IdType.AUTO)
    private Integer sysSmsTemplateId;
    private Integer sysProjectId;
    private Integer sysProjectType;
    private Integer sysSmsTemplateSort;
    private Integer sysSmsTemplateCategory;
    private String sysSmsTemplateName;
    private String sysSmsTemplateCode;
    private String sysSmsTemplateContent;
    private Integer sysSmsTemplateDelete;
    private Integer sysSmsTemplateStatus;
    private Integer sysSmsTemplateType;
    private Integer sysSmsTemplateDefault;
    private Integer sysSmsTemplateApprove;
    private String sysSmsTemplateApproveReject;
    private Integer sysSmsTemplateApproveCreateBy;
    private Integer sysSmsTemplateDisabled;
    private String sysSmsTemplateOrigin;
    private String sysSmsTemplateOriginUuid;
}
