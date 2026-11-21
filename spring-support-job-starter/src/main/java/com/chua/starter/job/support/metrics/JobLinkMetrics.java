package com.chua.starter.job.support.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 调度传输链路健康指标（进程内单例）。
 *
 * <p>采集「命令从调度中心走到执行器」这一段的可观测性计数：断线补偿重放了多少帧、
 * 近期命令缓冲的水位、丢单补记了多少条 SKIPPED、执行器幂等去重命中了多少次、
 * 执行器重连成功了多少次、日志队列溢出丢弃了多少条。</p>
 *
 * <p>这些计数原本只散落在各绑定器的日志里，出问题要靠翻日志拼时间线；这里收口成
 * 一个静态单例，由各写入点原地打点，治理接口 ({@code /v2/job/metrics}) 一次性读出。
 * 选择静态单例而不是 Spring bean：打点发生在同步绑定器 / 日志写入器这类
 * 不一定走容器装配的生命周期里，静态入口可以保证任何一处都能零成本计数。</p>
 *
 * <p>所有计数只增不减，重启归零——它回答的是「这一轮进程运行以来链路发生过什么」，
 * 不是跨周期的历史台账。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class JobLinkMetrics {

    /**
     * 近期命令缓冲的容量上限，与 SchedulerSyncBinder 的缓冲容量同一口径
     */
    public static final int COMMAND_BUFFER_CAPACITY = 256;

    /**
     * 近期命令缓冲的存活时长（毫秒），与 SchedulerSyncBinder 的重放窗口同一口径
     */
    public static final long COMMAND_BUFFER_TTL_MS = 120_000L;

    /**
     * 进程内唯一实例
     */
    private static final JobLinkMetrics INSTANCE = new JobLinkMetrics();

    /**
     * 断线补偿重放的命令帧数（调度中心侧）
     */
    private final AtomicLong replayedCommands = new AtomicLong();

    /**
     * 近期命令缓冲水位峰值（同时刻缓冲内最多积压过多少条）
     */
    private final AtomicLong commandBufferWatermark = new AtomicLong();

    /**
     * 丢单补记的 SKIPPED 日志条数（0 在线执行器时写入的那类记录）
     */
    private final AtomicLong missedDispatchRecords = new AtomicLong();

    /**
     * 执行器按消息 id 幂等去重命中的次数（重放帧与原始帧重复送达时被挡掉的次数）
     */
    private final AtomicLong executorDedupHits = new AtomicLong();

    /**
     * 执行器连接+注册成功次数（含首次上线与断线重连）
     */
    private final AtomicLong executorConnects = new AtomicLong();

    /**
     * 日志队列已满被丢弃的日志条数
     */
    private final AtomicLong logQueueDrops = new AtomicLong();

    private JobLinkMetrics() {
    }

    /**
     * 进程内唯一实例。
     *
     * @return 单例
     */
    public static JobLinkMetrics instance() {
        return INSTANCE;
    }

    /**
     * 累加补偿重放帧数。
     *
     * @param frames 本次重放的帧数
     */
    public void recordReplay(int frames) {
        if (frames > 0) {
            replayedCommands.addAndGet(frames);
        }
    }

    /**
     * 记录一次缓冲写入后的水位，保留峰值。
     *
     * @param size 当前缓冲深度
     */
    public void recordBufferDepth(int size) {
        if (size <= 0) {
            return;
        }
        long current;
        do {
            current = commandBufferWatermark.get();
            if (size <= current) {
                return;
            }
        } while (!commandBufferWatermark.compareAndSet(current, size));
    }

    /**
     * 累加丢单补记条数。
     */
    public void recordMissedDispatch() {
        missedDispatchRecords.incrementAndGet();
    }

    /**
     * 累加执行器幂等去重命中次数。
     */
    public void recordDedupHit() {
        executorDedupHits.incrementAndGet();
    }

    /**
     * 累加执行器连接+注册成功次数。
     */
    public void recordExecutorConnect() {
        executorConnects.incrementAndGet();
    }

    /**
     * 累加日志队列丢弃条数。
     */
    public void recordLogDrop() {
        logQueueDrops.incrementAndGet();
    }

    public long replayedCommands() {
        return replayedCommands.get();
    }

    public long commandBufferWatermark() {
        return commandBufferWatermark.get();
    }

    public long missedDispatchRecords() {
        return missedDispatchRecords.get();
    }

    public long executorDedupHits() {
        return executorDedupHits.get();
    }

    public long executorConnects() {
        return executorConnects.get();
    }

    public long logQueueDrops() {
        return logQueueDrops.get();
    }
}
