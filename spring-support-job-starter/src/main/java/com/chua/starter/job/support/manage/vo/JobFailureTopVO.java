package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.log.JobLogFailureTop;

/**
 * 执行失败次数排名出参。
 *
 * @param jobKey          处理器 Bean 名 (任务键)
 * @param failures        窗口内失败次数
 * @param lastFailureTime 最近一次失败触发时刻毫秒, 无记录时为 {@code null}
 * @since 4.0.0.42
 * @author CH
 */
public record JobFailureTopVO(
        String jobKey,
        long failures,
        Long lastFailureTime) {

    /**
     * 由聚合结果构造出参。
     *
     * @param top 聚合结果
     * @return 出参, 聚合结果为空时返回 {@code null}
     */
    public static JobFailureTopVO of(JobLogFailureTop top) {
        if (top == null) {
            return null;
        }
        return new JobFailureTopVO(top.jobKey(), top.failures(), top.lastFailureTimeMs());
    }
}
