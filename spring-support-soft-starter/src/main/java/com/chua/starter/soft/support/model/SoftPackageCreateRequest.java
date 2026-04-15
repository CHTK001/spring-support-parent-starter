package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@ApiModel("软件创建请求")
public class SoftPackageCreateRequest {

    @ApiModelProperty("仓库ID")
    private Integer softRepositoryId;

    @ApiModelProperty("软件编码")
    private String packageCode;

    @ApiModelProperty("软件名称")
    private String packageName;

    @ApiModelProperty("软件分类")
    private String packageCategory;

    @ApiModelProperty("画像编码")
    private String profileCode;

    @ApiModelProperty("操作系统")
    private String osType;

    @ApiModelProperty("架构")
    private String architecture;

    @ApiModelProperty("软件描述")
    private String description;

    @ApiModelProperty("图标地址")
    private String iconUrl;

    @ApiModelProperty("版本编码")
    private String versionCode;

    @ApiModelProperty("版本名称")
    private String versionName;

    @ApiModelProperty("下载地址列表")
    private List<String> downloadUrls = new ArrayList<>();

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

    @ApiModelProperty("安装脚本")
    private String installScript;

    @ApiModelProperty("初始化脚本")
    private String initScript;

    @ApiModelProperty("启动脚本")
    private String startScript;

    @ApiModelProperty("停止脚本")
    private String stopScript;

    @ApiModelProperty("卸载脚本")
    private String uninstallScript;

    @ApiModelProperty("服务注册脚本")
    private String serviceRegisterScript;

    @ApiModelProperty("服务卸载脚本")
    private String serviceUnregisterScript;

    @ApiModelProperty("是否启用版本")
    private Boolean enabled;

    @ApiModelProperty("是否接入 server-service")
    private Boolean integrateServerService;

    @ApiModelProperty("服务接入编码")
    private String serverServiceCode;

    @ApiModelProperty("服务接入名称")
    private String serverServiceName;

    @ApiModelProperty("服务类型")
    private String serverServiceType;

    @ApiModelProperty("服务启动方式")
    private String serverServiceStartMode;

    @ApiModelProperty("服务执行通道")
    private String serverExecutionProvider;
}
