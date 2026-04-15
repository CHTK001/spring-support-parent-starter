package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@ApiModel("软件版本创建请求")
public class SoftPackageVersionCreateRequest {

    @ApiModelProperty("版本编码")
    private String versionCode;

    @ApiModelProperty("版本显示名")
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

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("下载地址列表")
    private List<String> downloadUrls = new ArrayList<>();

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

    @ApiModelProperty("扩展元数据JSON")
    private String metadataJson;
}
