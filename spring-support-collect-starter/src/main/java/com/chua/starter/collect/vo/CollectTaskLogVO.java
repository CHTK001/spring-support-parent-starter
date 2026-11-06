package com.chua.starter.collect.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 节点执行日志出参。
 *
 * @param collectTaskLogId        主键ID
 * @param collectTaskLogTaskId    任务ID
 * @param collectTaskLogFlowId    该节点出自哪条流程，子流程节点日志与宿主任务同属一条任务但流程不同
 * @param collectTaskLogNodeId    节点ID
 * @param collectTaskLogNodeName  节点名称
 * @param collectTaskLogNodeShape 节点类型
 * @param collectTaskLogIteration 循环轮次
 * @param collectTaskLogStatus    结果 SUCCESS/FAILURE/SKIPPED
 * @param collectTaskLogCost      耗时毫秒
 * @param collectTaskLogMessage   摘要或异常信息
 * @param collectTaskLogOutput    出参预览
 * @param createTime              记录时间
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-节点日志出参")
public record CollectTaskLogVO(
        @Schema(description = "主键ID")
        Long collectTaskLogId,
        @Schema(description = "任务ID")
        Long collectTaskLogTaskId,
        @Schema(description = "归属流程ID")
        Integer collectTaskLogFlowId,
        @Schema(description = "节点ID")
        String collectTaskLogNodeId,
        @Schema(description = "节点名称")
        String collectTaskLogNodeName,
        @Schema(description = "节点类型")
        String collectTaskLogNodeShape,
        @Schema(description = "循环轮次")
        Integer collectTaskLogIteration,
        @Schema(description = "结果")
        String collectTaskLogStatus,
        @Schema(description = "耗时毫秒")
        Long collectTaskLogCost,
        @Schema(description = "摘要或异常信息")
        String collectTaskLogMessage,
        @Schema(description = "出参预览")
        String collectTaskLogOutput,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "记录时间")
        LocalDateTime createTime) {
}
