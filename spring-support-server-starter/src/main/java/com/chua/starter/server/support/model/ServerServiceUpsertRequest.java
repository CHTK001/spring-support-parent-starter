package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("服务器服务绑定请求")
public class ServerServiceUpsertRequest {

    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @ApiModelProperty("接入类型")
    private String serverType;

    @ApiModelProperty("主机地址")
    private String host;

    @ApiModelProperty("端口")
    private Integer port;

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("操作系统")
    private String osType;

    @ApiModelProperty("服务名称")
    private String serviceName;

    @ApiModelProperty("服务类型")
    private String serviceType;

    @ApiModelProperty("关联软件ID")
    private Integer softPackageId;

    @ApiModelProperty("关联软件版本ID")
    private Integer softPackageVersionId;

    @ApiModelProperty("关联安装ID")
    private Integer softInstallationId;

    @ApiModelProperty("安装目录")
    private String installPath;

    @ApiModelProperty("配置路径JSON")
    private String configPathsJson;

    @ApiModelProperty("日志路径JSON")
    private String logPathsJson;

    @ApiModelProperty("配置模板")
    private String configTemplate;

    @ApiModelProperty("初始化脚本")
    private String initScript;

    @ApiModelProperty("安装脚本")
    private String installScript;

    @ApiModelProperty("卸载脚本")
    private String uninstallScript;

    @ApiModelProperty("检测脚本")
    private String detectScript;

    @ApiModelProperty("注册脚本")
    private String registerScript;

    @ApiModelProperty("取消注册脚本")
    private String unregisterScript;

    @ApiModelProperty("启动脚本")
    private String startScript;

    @ApiModelProperty("停止脚本")
    private String stopScript;

    @ApiModelProperty("重启脚本")
    private String restartScript;

    @ApiModelProperty("状态脚本")
    private String statusScript;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("扩展元数据")
    private String metadataJson;
}
