package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.log.JobLogTrendPoint;

/**
 * 执行日志小时趋势出参。
 *
 * @param time    桶起点 (整点毫秒)
 * @param total   该小时总执行次数
 * @param success 执行成功次数
 * @param failure 执行失败次数
 * @param skipped 执行跳过次数
 * @since 4.0.0.42
 * @author CH
 */
public record JobTrendPointVO(
        long time,
        long total,
        long success,
        long failure,
        long skipped) {

    /**
     * 由聚合结果构造出参。
     *
     * @param point 聚合结果
     * @return 出参, 聚合结果为空时返回 {@code null}
     */
    public static JobTrendPointVO of(JobLogTrendPoint point) {
        if (point == null) {
            return null;
        }
        return new JobTrendPointVO(point.bucketStartMs(), point.total(),
                point.success(), point.failure(), point.skipped());
    }
}
