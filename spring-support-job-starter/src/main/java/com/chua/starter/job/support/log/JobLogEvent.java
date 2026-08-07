package com.chua.starter.job.support.log;

import java.time.Instant;

/**
 * 任务执行日志事件 (record)。
 *
 * <p>由 EXECUTOR 端的 {@link com.chua.starter.job.support.execute.JobExecutor}
 * 在 handler.invoke 之后构造, 经 {@link com.chua.starter.job.support.sync.ExecutorSyncBinder}
 * 上行到 SCHEDULER, 由 {@link com.chua.starter.job.support.sync.SchedulerSyncBinder}
 * 写入 {@link JobLogSink}。</p>
 *
 * <p>SCHEDULER 自身的本地执行 (SINGLE 模式) 同样适用本事件,
 * 直接在进程内构造后提交给 sink。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public record JobLogEvent(
        String jobName,
        Status status,
        long costMs,
        String exception,
        long executedAtMs,
        String nodeId,
        Instant timestamp
) {

    /**
     * 状态枚举。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public enum Status {
        /**
         * 执行成功
         */
        SUCCESS,
        /**
         * 执行失败
         */
        FAILURE,
        /**
         * 跳过 (handler 不存在等)
         */
        SKIPPED
    }

    /**
     * 构造成功事件。
     *
     * @param jobName     任务名
     * @param costMs      耗时 (毫秒)
     * @param executedAtMs 执行开始时间戳
     * @param nodeId      当前节点
     * @return 事件
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId) {
        return new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now());
    }

    /**
     * 构造失败事件。
     *
     * @param jobName     任务名
     * @param costMs      耗时 (毫秒)
     * @param throwable   异常
     * @param executedAtMs 执行开始时间戳
     * @param nodeId      当前节点
     * @return 事件
     */
    public static JobLogEvent failure(String jobName, long costMs, Throwable throwable,
                                      long executedAtMs, String nodeId) {
        String message = throwable == null ? null : throwable.getMessage();
        return new JobLogEvent(jobName, Status.FAILURE, costMs, message,
                executedAtMs, nodeId, Instant.now());
    }

    /**
     * 构造跳过事件。
     *
     * @param jobName 任务名
     * @param reason  原因
     * @param nodeId  当前节点
     * @return 事件
     */
    public static JobLogEvent skipped(String jobName, String reason, String nodeId) {
        return new JobLogEvent(jobName, Status.SKIPPED, 0L, reason,
                System.currentTimeMillis(), nodeId, Instant.now());
    }
}
