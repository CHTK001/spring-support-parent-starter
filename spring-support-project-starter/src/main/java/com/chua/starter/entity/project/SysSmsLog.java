package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sys_sms_log")
public class SysSmsLog extends SysBase {

    @TableId(value = "sys_sms_log_id", type = IdType.AUTO)
    private Integer sysSmsLogId;
    private Integer sysProjectId;
    private String sysSmsLogTarget;
    private String sysSmsLogTemplateId;
    private String sysSmsLogContent;
    private String sysSmsLogParam;
    private BigDecimal sysSmsCost;
    private String sysSmsCode;
    private String sysSmsMessage;
    private String sysSmsAppType;
    private String sysSmsAppId;
    private String sysSmsAppSign;
}
