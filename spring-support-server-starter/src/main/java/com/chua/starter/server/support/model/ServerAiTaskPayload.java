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

    @ApiModelProperty("指标类型")
    private String metricType;

    @ApiModelProperty("告警级别")
    private String severity;

    @ApiModelProperty("操作日志ID")
    private Integer operationLogId;

    @ApiModelProperty("历史分析分钟范围")
    private Integer minutes;

    @ApiModelProperty("历史开始时间戳")
    private Long startTime;

    @ApiModelProperty("历史结束时间戳")
    private Long endTime;

    @ApiModelProperty("历史状态过滤")
    private String stateFilter;

    @ApiModelProperty("当前过滤条件对应的唯一键")
    private String filterKey;

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
