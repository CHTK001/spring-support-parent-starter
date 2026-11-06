package com.chua.starter.collect.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 采集任务出参。
 *
 * @param collectTaskId          主键ID
 * @param collectTaskNo         任务号
 * @param collectTaskFlowId     所属流程ID
 * @param collectTaskFlowName   流程名称快照
 * @param collectTaskStatus     状态 RUNNING/SUCCESS/FAILURE/STOPPED
 * @param collectTaskTriggerType 触发方式 CRON/MANUAL/TEST
 * @param collectTaskParams     本次执行入参覆盖（JSON）
 * @param collectTaskBeginTime  开始时间
 * @param collectTaskEndTime    结束时间
 * @param collectTaskCost       耗时毫秒
 * @param collectTaskNodeCount  执行节点次数
 * @param collectTaskRequestCount 发起抓取次数
 * @param collectTaskRecordCount  产出记录数
 * @param collectTaskError      失败原因
 * @param collectTaskExecutorId 执行节点标识
 * @param running               是否仍在本机执行
 * @param createName            创建人姓名
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-任务出参")
public record CollectTaskVO(
        @Schema(description = "主键ID")
        Long collectTaskId,
        @Schema(description = "任务号")
        String collectTaskNo,
        @Schema(description = "所属流程ID")
        Integer collectTaskFlowId,
        @Schema(description = "流程名称快照")
        String collectTaskFlowName,
        @Schema(description = "状态")
        String collectTaskStatus,
        @Schema(description = "触发方式")
        String collectTaskTriggerType,
        @Schema(description = "执行入参JSON")
        String collectTaskParams,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "开始时间")
        LocalDateTime collectTaskBeginTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "结束时间")
        LocalDateTime collectTaskEndTime,
        @Schema(description = "耗时毫秒")
        Long collectTaskCost,
        @Schema(description = "执行节点次数")
        Integer collectTaskNodeCount,
        @Schema(description = "发起抓取次数")
        Integer collectTaskRequestCount,
        @Schema(description = "产出记录数")
        Integer collectTaskRecordCount,
        @Schema(description = "失败原因")
        String collectTaskError,
        @Schema(description = "执行节点标识")
        String collectTaskExecutorId,
        @Schema(description = "是否运行中")
        Boolean running,
        @Schema(description = "创建人姓名")
        String createName) {
}
