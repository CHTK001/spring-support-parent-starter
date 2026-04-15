package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("软件版本档")
@TableName("soft_package_version")
public class SoftPackageVersion extends SysBase {

    @TableId(value = "soft_package_version_id", type = IdType.AUTO)
    @ApiModelProperty("软件版本ID")
    private Integer softPackageVersionId;

    @ApiModelProperty("软件ID")
    private Integer softPackageId;

    @ApiModelProperty("版本编码")
    private String versionCode;

    @ApiModelProperty("版本名称")
    private String versionName;

    @ApiModelProperty("版本软件名称")
    private String packageName;

    @ApiModelProperty("版本操作系统")
    private String osType;

    @ApiModelProperty("版本架构")
    private String architecture;

    @ApiModelProperty("来源分类")
    private String sourceKind;

    @ApiModelProperty("安装模式")
    private String installMode;

    @ApiModelProperty("来源ID")
    private Integer repositorySourceId;

    @ApiModelProperty("本地包路径")
    private String artifactPath;

    @ApiModelProperty("远程下载地址")
    private String downloadUrl;

    @ApiModelProperty("模板来源版本ID")
    private Integer templateFromVersionId;

    @ApiModelProperty("下载地址JSON")
    private String downloadUrlsJson;

    @ApiModelProperty("MD5")
    private String md5;

    @ApiModelProperty("SHA256")
    private String sha256;

    @ApiModelProperty("安装脚本")
    private String installScript;

    @ApiModelProperty("卸载脚本")
    private String uninstallScript;

    @ApiModelProperty("启动脚本")
    private String startScript;

    @ApiModelProperty("停止脚本")
    private String stopScript;

    @ApiModelProperty("重启脚本")
    private String restartScript;

    @ApiModelProperty("状态脚本")
    private String statusScript;

    @ApiModelProperty("服务注册脚本")
    private String serviceRegisterScript;

    @ApiModelProperty("服务卸载脚本")
    private String serviceUnregisterScript;

    @ApiModelProperty("日志路径JSON")
    private String logPathsJson;

    @ApiModelProperty("配置路径JSON")
    private String configPathsJson;

    @ApiModelProperty("能力标记JSON")
    private String capabilityFlagsJson;

    @ApiModelProperty("扩展元数据")
    private String metadataJson;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @TableField(exist = false)
    @ApiModelProperty("下载地址")
    private List<String> downloadUrls;

    @TableField(exist = false)
    @ApiModelProperty("软件编码")
    private String packageCode;

    @TableField(exist = false)
    @ApiModelProperty("日志路径")
    private List<String> logPaths;

    @TableField(exist = false)
    @ApiModelProperty("配置路径")
    private List<String> configPaths;

    @TableField(exist = false)
    @ApiModelProperty("能力标记")
    private List<String> capabilityFlags;
}
