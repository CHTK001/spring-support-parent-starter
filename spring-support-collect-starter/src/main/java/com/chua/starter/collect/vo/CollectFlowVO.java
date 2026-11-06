package com.chua.starter.collect.vo;

import com.chua.starter.collect.dto.CollectDslDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 流程定义出参。
 *
 * <p>列表接口不填充 {@code dsl}，详情与设计器接口才填充，避免分页响应里塞满流程图。</p>
 *
 * @param collectFlowId              主键ID
 * @param collectFlowName            流程名称
 * @param collectFlowCategoryId      所属分类ID
 * @param collectFlowCategoryName   所属分类名称
 * @param collectFlowCode           流程编码
 * @param collectFlowCron           定时表达式
 * @param collectFlowStatus         调度状态，0 停用 1 启用
 * @param collectFlowConcurrency    同一流程允许并发运行的任务数
 * @param collectFlowThreadCount    流程内并行线程数
 * @param collectFlowRemark         备注
 * @param collectFlowLastExecTime   最近一次开始执行时间
 * @param collectFlowNextExecTime   下次触发时间，取自调度台账，未登记时为空
 * @param collectFlowExecCount      累计执行次数
 * @param collectFlowRecordCount    累计采集条数，随任务收尾累加
 * @param nodeCount                 节点数，来自 DSL 摘要
 * @param edgeCount                 连线数，来自 DSL 摘要
 * @param running                   本机是否有该流程的运行中任务
 * @param scheduled                 调度台账里已登记该流程，且任务处理器可用
 * @param dsl                       流程图 DSL，仅详情接口返回
 * @param createName                创建人姓名
 * @param createTime                 创建时间
 * @param updateTime                 更新时间
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-流程出参")
public record CollectFlowVO(
        @Schema(description = "主键ID")
        Integer collectFlowId,
        @Schema(description = "流程名称")
        String collectFlowName,
        @Schema(description = "所属分类ID")
        Integer collectFlowCategoryId,
        @Schema(description = "所属分类名称")
        String collectFlowCategoryName,
        @Schema(description = "流程编码")
        String collectFlowCode,
        @Schema(description = "定时表达式")
        String collectFlowCron,
        @Schema(description = "调度状态 0停用 1启用")
        Integer collectFlowStatus,
        @Schema(description = "允许并发任务数")
        Integer collectFlowConcurrency,
        @Schema(description = "流程内并行线程数")
        Integer collectFlowThreadCount,
        @Schema(description = "备注")
        String collectFlowRemark,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "最近执行时间")
        LocalDateTime collectFlowLastExecTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "下次触发时间")
        LocalDateTime collectFlowNextExecTime,
        @Schema(description = "累计执行次数")
        Integer collectFlowExecCount,
        @Schema(description = "累计采集条数")
        Long collectFlowRecordCount,
        @Schema(description = "节点数")
        Integer nodeCount,
        @Schema(description = "连线数")
        Integer edgeCount,
        @Schema(description = "是否运行中")
        Boolean running,
        @Schema(description = "调度已登记")
        Boolean scheduled,
        @Schema(description = "流程图DSL")
        CollectDslDTO dsl,
        @Schema(description = "创建人姓名")
        String createName,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "创建时间")
        LocalDateTime createTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "更新时间")
        LocalDateTime updateTime) {
}
