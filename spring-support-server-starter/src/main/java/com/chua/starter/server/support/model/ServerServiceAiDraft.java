package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器服务AI主档草稿")
public class ServerServiceAiDraft {

    @ApiModelProperty("摘要说明")
    private String summary;

    @ApiModelProperty("服务描述")
    private String description;

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

    @ApiModelProperty("AI提供商")
    private String provider;

    @ApiModelProperty("AI模型")
    private String model;
}
