package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 同步执行日志健康度总览（「同步日志」页顶部统计卡 + 近 24 小时趋势）。
 *
 * <p>聚合口径：只统计库内落盘记录（{@code ms_sync_task_run_log}），
 * 进行中的执行未落库、不在统计范围。成功率 = 成功数 / (成功数 + 失败数)。</p>
 *
 * @param total        执行总数
 * @param success      成功次数
 * @param failed       失败次数
 * @param successRate  成功率（0~100，保留一位小数；无记录为 0）
 * @param avgCostMs    平均耗时毫秒（无记录为 0）
 * @param hourly       近 24 小时逐小时执行分布（新在前，最多 24 个）
 * @param failureGroups 失败原因分组（按 ERROR 行关键文案归类，最多返回 N 组）
 * @author CH
 * @since 4.0.0.45
 */
public record SyncRunLogStatsVO(
        long total,
        long success,
        long failed,
        double successRate,
        long avgCostMs,
        List<Hourly> hourly,
        List<FailureGroup> failureGroups) {

    /**
     * 一小时窗口内的执行分布。
     *
     * @param hour    整点时刻（{@code yyyy-MM-dd HH:00}）
     * @param total   该小时内执行次数
     * @param success 该小时内成功次数
     * @param failed  该小时内失败次数
     */
    public record Hourly(String hour, long total, long success, long failed) {
    }

    /**
     * 一类失败原因（按 ERROR 行文本的稳定关键段归类）。
     *
     * @param label 原因标签（人类可读）
     * @param count 出现次数
     */
    public record FailureGroup(String label, long count) {
    }
}
