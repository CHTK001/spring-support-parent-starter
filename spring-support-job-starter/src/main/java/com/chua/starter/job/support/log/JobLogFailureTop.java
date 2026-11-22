package com.chua.starter.job.support.log;

/**
 * 执行失败次数排名中的一个任务。
 *
 * @param jobKey            处理器 Bean 名 (任务键)
 * @param failures          窗口内失败次数
 * @param lastFailureTimeMs 最近一次失败触发时刻毫秒, 无记录时为 {@code null}
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogFailureTop(
        String jobKey,
        long failures,
        Long lastFailureTimeMs) {
}
