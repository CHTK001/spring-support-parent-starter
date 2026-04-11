package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("软件版本轻量整理请求")
public class SoftPackageVersionUpdateRequest {

    @ApiModelProperty("版本显示名")
    private String versionName;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("下载地址JSON")
    private String downloadUrlsJson;

    @ApiModelProperty("安装脚本")
    private String installScript;

    @ApiModelProperty("启动脚本")
    private String startScript;

    @ApiModelProperty("停止脚本")
    private String stopScript;

    @ApiModelProperty("重启脚本")
    private String restartScript;

    @ApiModelProperty("状态脚本")
    private String statusScript;

    @ApiModelProperty("日志路径JSON")
    private String logPathsJson;

    @ApiModelProperty("配置路径JSON")
    private String configPathsJson;

    @ApiModelProperty("扩展元数据JSON")
    private String metadataJson;
}
