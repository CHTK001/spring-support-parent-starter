package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.log.JobLogDurationStats;

/**
 * 执行耗时统计出参。
 *
 * @param total     窗口内日志总行数 (含未记录耗时的行)
 * @param avgCostMs 平均耗时毫秒, 无样本时为 {@code null}
 * @param maxCostMs 最大耗时毫秒, 无样本时为 {@code null}
 * @param p95CostMs P95 耗时毫秒, 无样本时为 {@code null}
 * @since 4.0.0.42
 * @author CH
 */
public record JobDurationStatsVO(
        long total,
        Double avgCostMs,
        Long maxCostMs,
        Long p95CostMs) {

    /**
     * 由聚合结果构造出参。
     *
     * @param stats 聚合结果
     * @return 出参, 聚合结果为空时返回 {@code null}
     */
    public static JobDurationStatsVO of(JobLogDurationStats stats) {
        if (stats == null) {
            return null;
        }
        return new JobDurationStatsVO(stats.total(), stats.avgCostMs(),
                stats.maxCostMs(), stats.p95CostMs());
    }
}
