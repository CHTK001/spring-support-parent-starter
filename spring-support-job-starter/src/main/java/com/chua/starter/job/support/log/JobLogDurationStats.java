package com.chua.starter.job.support.log;

/**
 * 执行耗时统计。
 *
 * <p>平均 / 最大 / P95 只统计记录了耗时的日志行 ({@code sys_job_log_cost} 非空);
 * {@code total} 是窗口内日志总行数, 两者口径不同, 展示时不要混用。</p>
 *
 * @param total     窗口内日志总行数 (含未记录耗时的行)
 * @param avgCostMs 平均耗时毫秒, 无样本时为 {@code null}
 * @param maxCostMs 最大耗时毫秒, 无样本时为 {@code null}
 * @param p95CostMs P95 耗时毫秒, 无样本时为 {@code null}
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogDurationStats(
        long total,
        Double avgCostMs,
        Long maxCostMs,
        Long p95CostMs) {
}
