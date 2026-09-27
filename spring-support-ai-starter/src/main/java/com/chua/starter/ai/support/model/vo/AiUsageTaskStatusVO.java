package com.chua.starter.ai.support.model.vo;

/**
 * 用量聚合任务运行状态
 *
 * @param aggregateRunning     当天聚合任务是否在跑
 * @param aggregateAllRunning  全量回填任务是否在跑
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsageTaskStatusVO(
        boolean aggregateRunning,
        boolean aggregateAllRunning
) {
}
