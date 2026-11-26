package com.chua.starter.collect.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台分页行，最近任务仅查询本页流程，每个流程最多五条。
 *
 * @param flow 流程摘要，不含 DSL
 * @param recentTasks 最近任务，按开始时间及任务 ID 倒序
 * @author CH
 */
public record CollectFlowWorkbenchVO(CollectFlowVO flow, List<RunSummary> recentTasks) {

    /**
     * 工作台运行摘要，不携带任务参数、日志或结果正文。
     *
     * @param id 任务 ID
     * @param no 任务号
     * @param status 任务状态
     * @param triggerType 触发方式
     * @param beginTime 开始时间
     * @param cost 耗时毫秒
     * @param recordCount 产出条数
     * @param error 失败原因
     */
    public record RunSummary(Long id, String no, String status, String triggerType,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
                             LocalDateTime beginTime,
                             Long cost, Integer recordCount, String error) {
    }
}
