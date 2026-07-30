package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器服务AI诊断")
public class ServerServiceAiAdvice {

    @ApiModelProperty("失败原因")
    private String reason;

    @ApiModelProperty("处理方案")
    private String solution;

    @ApiModelProperty("修复脚本")
    private String fixScript;

    @ApiModelProperty("AI提供商")
    private String provider;

    @ApiModelProperty("AI模型")
    private String model;
}
