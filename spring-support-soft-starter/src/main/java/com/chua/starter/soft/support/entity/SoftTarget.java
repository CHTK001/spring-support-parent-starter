package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.mybatis.type.EncryptTypeHandler;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("软件安装目标")
@TableName(value = "soft_target", autoResultMap = true)
public class SoftTarget extends SysBase {

    @TableId(value = "soft_target_id", type = IdType.AUTO)
    @ApiModelProperty("目标ID")
    private Integer softTargetId;

    @ApiModelProperty("目标名称")
    private String targetName;

    @ApiModelProperty("目标编码")
    private String targetCode;

    @ApiModelProperty("目标类型")
    private String targetType;

    @ApiModelProperty("操作系统")
    private String osType;

    @ApiModelProperty("架构")
    private String architecture;

    @ApiModelProperty("主机")
    private String host;

    @ApiModelProperty("端口")
    private Integer port;

    @ApiModelProperty("账号")
    private String username;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("密码")
    private String password;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("私钥")
    private String privateKey;

    @ApiModelProperty("基础目录")
    private String baseDirectory;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("扩展元数据")
    private String metadataJson;
}
