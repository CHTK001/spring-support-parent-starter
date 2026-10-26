package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 单个任务的执行耗时趋势（「同步日志」页耗时曲线图数据源）。
 *
 * <p>按执行时间升序返回最近 {@code limit} 次落盘执行，每次一个点；
 * 失败执行也计入（用于在曲线上标红，直观看出「哪次失败了」）。</p>
 *
 * @param taskId   任务 ID
 * @param taskName 任务名（任务已删除为回退文案）
 * @param points   耗时序列（按执行时间升序，新执行在末尾）
 * @author CH
 * @since 4.0.0.45
 */
public record SyncRunLogTrendVO(
        Long taskId,
        String taskName,
        List<Point> points) {

    /**
     * 一次执行的耗时点。
     *
     * @param runTime 执行开始时刻（{@code yyyy-MM-dd HH:mm:ss}）
     * @param costMs  耗时（毫秒）
     * @param status  {@code SUCCESS} / {@code FAILED}
     */
    public record Point(String runTime, Long costMs, String status) {
    }
}
