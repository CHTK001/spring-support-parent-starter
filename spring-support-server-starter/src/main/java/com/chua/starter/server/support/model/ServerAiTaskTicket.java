package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器 AI 异步任务票据")
public class ServerAiTaskTicket {

    @ApiModelProperty("任务ID")
    private String taskId;

    @ApiModelProperty("任务类型")
    private String taskType;

    @ApiModelProperty("任务状态")
    private String status;

    @ApiModelProperty("服务器服务ID")
    private Integer serverServiceId;

    @ApiModelProperty("操作日志ID")
    private Integer operationLogId;

    @ApiModelProperty("任务说明")
    private String message;
}
