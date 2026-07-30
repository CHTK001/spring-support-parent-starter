package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_email_log")
public class SysEmailLog {

    @TableId(value = "sys_email_log_id", type = IdType.AUTO)
    private Integer sysEmailLogId;
    private String sysEmailLogTarget;
    private String sysEmailLogTemplateId;
    private String sysEmailLogParam;
    private BigDecimal sysEmailCost;
    private String sysEmailCode;
    private String sysEmailMessage;
    private String sysEmailLogContent;
    private String sysEmailAppType;
    private String sysEmailAppId;
    private String sysEmailAppSign;
    private Integer sysProjectId;
    private Integer sysEmailRead;
    private String createName;
    private Integer createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String updateName;
    private Integer updateBy;
}
