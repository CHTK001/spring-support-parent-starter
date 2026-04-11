package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器服务执行结果")
public class ServerServiceCommandResult {

    @ApiModelProperty("服务器服务ID")
    private Integer serverServiceId;

    @ApiModelProperty("服务名称")
    private String serviceName;

    @ApiModelProperty("操作类型")
    private String operationType;

    @ApiModelProperty("是否成功")
    private boolean success;

    @ApiModelProperty("退出码")
    private Integer exitCode;

    @ApiModelProperty("执行说明")
    private String message;

    @ApiModelProperty("执行输出")
    private String output;

    @ApiModelProperty("运行状态")
    private String runtimeStatus;

    @ApiModelProperty("操作日志ID")
    private Integer operationLogId;

    @ApiModelProperty("AI失败原因")
    private String aiReason;

    @ApiModelProperty("AI处理方案")
    private String aiSolution;

    @ApiModelProperty("AI修复脚本")
    private String aiFixScript;

    @ApiModelProperty("AI提供商")
    private String aiProvider;

    @ApiModelProperty("AI模型")
    private String aiModel;

    @ApiModelProperty("知识库ID")
    private Integer knowledgeId;

    @ApiModelProperty("AI任务ID")
    private String taskId;

    @ApiModelProperty("AI任务状态")
    private String aiTaskStatus;
}
