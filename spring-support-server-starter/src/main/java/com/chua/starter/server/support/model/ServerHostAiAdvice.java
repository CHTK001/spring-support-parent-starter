package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器主机 AI 稳定性分析")
public class ServerHostAiAdvice {

    @ApiModelProperty("稳定性结论")
    private String summary;

    @ApiModelProperty("风险等级")
    private String riskLevel;

    @ApiModelProperty("处置建议")
    private String suggestion;

    @ApiModelProperty("AI 提供商")
    private String provider;

    @ApiModelProperty("AI 模型")
    private String model;
}
