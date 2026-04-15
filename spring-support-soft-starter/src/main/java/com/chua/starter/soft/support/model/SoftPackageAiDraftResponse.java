package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@ApiModel("软件新增 AI 草稿响应")
public class SoftPackageAiDraftResponse {

    @ApiModelProperty("一句话摘要")
    private String summary;

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

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("图标地址")
    private String iconUrl;

    @ApiModelProperty("版本编码")
    private String versionCode;

    @ApiModelProperty("版本名称")
    private String versionName;

    @ApiModelProperty("下载地址")
    private List<String> downloadUrls = new ArrayList<>();

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

    @ApiModelProperty("版本是否启用")
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

    @ApiModelProperty("是否来自 AI")
    private Boolean aiGenerated;

    @ApiModelProperty("AI 提供方")
    private String provider;

    @ApiModelProperty("AI 模型")
    private String model;

    @ApiModelProperty("回退原因")
    private String fallbackReason;

    @ApiModelProperty("提示信息")
    private String message;
}
