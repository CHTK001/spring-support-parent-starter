package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

@Data
@TableName("sys_secret")
public class SysSecret extends SysBase {

    @TableId(value = "sys_secret_id", type = IdType.AUTO)
    private Integer sysSecretId;
    private String sysSecretGroup;
    private String sysSecretCode;
    private String sysSecretFunction;
    private String sysSecretApiAddress;
    private String sysSecretAppId;
    private String sysSecretAppSecret;
    private String sysSecretSign;
    private Integer sysSecretDelete;
    private Integer sysSecretStatus;
    private String sysSecretCdn;
    private String sysSecretEndpoint;
    private String sysSecretRemark;
}
