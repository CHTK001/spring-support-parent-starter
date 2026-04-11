package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("安装实例")
@TableName("soft_installation")
public class SoftInstallation extends SysBase {

    @TableId(value = "soft_installation_id", type = IdType.AUTO)
    @ApiModelProperty("安装ID")
    private Integer softInstallationId;

    @ApiModelProperty("软件ID")
    private Integer softPackageId;

    @ApiModelProperty("版本ID")
    private Integer softPackageVersionId;

    @ApiModelProperty("目标ID")
    private Integer softTargetId;

    @ApiModelProperty("实例名称")
    private String installationName;

    @ApiModelProperty("安装路径")
    private String installPath;

    @ApiModelProperty("服务名称")
    private String serviceName;

    @ApiModelProperty("安装参数快照JSON")
    private String installOptionsJson;

    @ApiModelProperty("服务参数快照JSON")
    private String serviceOptionsJson;

    @ApiModelProperty("配置参数快照JSON")
    private String configOptionsJson;

    @ApiModelProperty("模板摘要JSON")
    private String templateSummaryJson;

    @ApiModelProperty("安装状态")
    private String installStatus;

    @ApiModelProperty("运行状态")
    private String runtimeStatus;

    @ApiModelProperty("已安装版本")
    private String installedVersion;

    @ApiModelProperty("安装时间")
    private LocalDateTime installedTime;

    @ApiModelProperty("最后操作时间")
    private LocalDateTime lastOperationTime;

    @ApiModelProperty("最后操作说明")
    private String lastOperationMessage;

    @TableField(exist = false)
    @ApiModelProperty("软件名称")
    private String packageName;

    @TableField(exist = false)
    @ApiModelProperty("版本名称")
    private String versionName;

    @TableField(exist = false)
    @ApiModelProperty("目标名称")
    private String targetName;
}
