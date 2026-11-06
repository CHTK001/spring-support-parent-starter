package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 流程保存入参。
 *
 * <p>设计器一次提交完整图：流程基本信息 + DSL 对象。服务层校验 DSL 后才落库，
 * 因此不存在"存了张跑不通的图"的状态。</p>
 *
 * @param collectFlowId           主键ID，新增留空
 * @param collectFlowName         流程名称
 * @param collectFlowCategoryId   所属分类ID，留空归入未分类 0
 * @param collectFlowCode         流程编码，留空由服务层生成
 * @param collectFlowCron         定时表达式，留空表示只允许手动运行
 * @param collectFlowConcurrency  同一流程允许并发运行的任务数
 * @param collectFlowThreadCount  流程内并行线程数
 * @param collectFlowRemark       备注
 * @param dsl                     流程图 DSL
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "流程保存入参")
public record CollectFlowSaveDTO(
        @Schema(description = "主键ID，新增留空")
        Integer collectFlowId,
        @Schema(description = "流程名称")
        String collectFlowName,
        @Schema(description = "所属分类ID")
        Integer collectFlowCategoryId,
        @Schema(description = "流程编码，留空自动生成")
        String collectFlowCode,
        @Schema(description = "定时表达式 6字段")
        String collectFlowCron,
        @Schema(description = "允许并发任务数")
        Integer collectFlowConcurrency,
        @Schema(description = "流程内并行线程数")
        Integer collectFlowThreadCount,
        @Schema(description = "备注")
        String collectFlowRemark,
        @Schema(description = "流程图DSL")
        CollectDslDTO dsl) {
}
