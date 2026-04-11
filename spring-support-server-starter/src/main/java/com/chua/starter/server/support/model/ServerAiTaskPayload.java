package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器 AI 异步任务事件")
public class ServerAiTaskPayload {

    @ApiModelProperty("任务ID")
    private String taskId;

    @ApiModelProperty("任务类型")
    private String taskType;

    @ApiModelProperty("任务状态")
    private String status;

    @ApiModelProperty("服务器服务ID")
    private Integer serverServiceId;

    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @ApiModelProperty("操作日志ID")
    private Integer operationLogId;

    @ApiModelProperty("任务消息")
    private String message;

    @ApiModelProperty("AI 失败原因")
    private String aiReason;

    @ApiModelProperty("AI 处理方案")
    private String aiSolution;

    @ApiModelProperty("AI 修复脚本")
    private String aiFixScript;

    @ApiModelProperty("AI 提供商")
    private String aiProvider;

    @ApiModelProperty("AI 模型")
    private String aiModel;

    @ApiModelProperty("知识库ID")
    private Integer knowledgeId;

    @ApiModelProperty("AI 草稿")
    private ServerServiceAiDraft draft;

    @ApiModelProperty("完成时间戳")
    private Long finishedAt;
}
