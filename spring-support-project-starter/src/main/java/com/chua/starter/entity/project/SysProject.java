package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

@Data
@TableName("sys_project")
public class SysProject extends SysBase {

    @TableId(value = "sys_project_id", type = IdType.AUTO)
    private Integer sysProjectId;
    private String sysProjectName;
    private String sysProjectIcon;
    private String sysProjectGroup;
    private String sysProjectCode;
    private String sysProjectAppId;
    private String sysProjectAppSecret;
    private String sysProjectSign;
    private String sysProjectAppKey;
    private String sysProjectSignCode;
    private Integer sysProjectDelete;
    private Integer sysProjectStatus;
    private String sysProjectCdn;
    private String sysProjectEndpoint;
    private String sysProjectLocalPath;
    private String sysProjectApiAddress;
    private String sysProjectLocationTempPath;
    private String sysProjectLocationOssAddress;
    private String sysProjectVender;
    private Integer sysProjectSort;
    private String sysProjectRemark;
    private String sysProjectFunction;
    private String sysProjectSmtpHost;
    private Integer sysProjectSmtpPort;
    private String sysProjectSmtpPassword;
    private String sysProjectSmtpFrom;
    @TableField(exist = false)
    private String sysProjectFunctionDefaultLabel;
    @TableField(exist = false)
    private String sysProjectFunctionDefaultIds;
    @TableField(exist = false)
    private Integer sysProjectFunctionDefault;
}
