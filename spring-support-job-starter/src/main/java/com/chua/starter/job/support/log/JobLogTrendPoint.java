package com.chua.starter.job.support.log;

/**
 * 执行日志按小时聚合出的一个趋势点。
 *
 * <p>桶起点是整点毫秒时间戳, 与日志行 {@code triggerTimeMs} 同基准, 前端可直接格式化;
 * 窗口内没有日志的桶不会出现在结果里, 是否补零由调用方决定。</p>
 *
 * @param bucketStartMs 桶起点 (整点毫秒)
 * @param total         该小时总执行次数
 * @param success       执行成功次数
 * @param failure       执行失败次数
 * @param skipped       执行跳过次数
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogTrendPoint(
        long bucketStartMs,
        long total,
        long success,
        long failure,
        long skipped) {
}
