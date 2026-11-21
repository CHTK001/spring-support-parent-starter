package com.chua.starter.job.support.manage.vo;

/**
 * 传输链路健康指标出参。
 *
 * <p>把「调度中心 → 同步通道 → 执行器 → 日志回流」这条链路上的自愈与损耗计数一次给全：
 * 断线补偿重放、命令缓冲水位、丢单补记、执行器幂等去重、执行器重连、日志队列丢弃，
 * 以及执行器本体的处理/丢弃/并发跳过计数。前端「传输链路健康」面板直接消费，
 * 不必再从日志里拼时间线。</p>
 *
 * @param replayedCommands         断线补偿重放的命令帧数（调度中心侧）
 * @param commandBufferWatermark   近期命令缓冲水位峰值（同时刻最多积压过多少条）
 * @param commandBufferCapacity    近期命令缓冲容量上限（超出淘汰最旧）
 * @param commandBufferTtlMs       近期命令缓冲存活时长（毫秒），重放只补该窗口内的命令
 * @param missedDispatchRecords    丢单补记的 SKIPPED 日志条数（0 在线执行器时写入）
 * @param executorDedupHits        执行器按消息 id 幂等去重命中次数（重放帧与原始帧重复时被挡掉）
 * @param executorConnects         执行器连接+注册成功次数（含首次上线与断线重连）
 * @param logQueueDrops            日志队列已满被丢弃的日志条数
 * @param executorOnline           执行器计数是否可用（本进程装配了 JobExecutor 才有值）
 * @param executorProcessed        执行器累计已处理任务数（executorOnline=false 时为 0）
 * @param executorDropped          执行器累计因线程池满被丢弃的任务数（executorOnline=false 时为 0）
 * @param executorConcurrentSkipped 执行器累计因并发策略被跳过的任务数（executorOnline=false 时为 0）
 * @since 4.0.0.51
 * @author CH
 */
public record JobLinkMetricsVO(
        long replayedCommands,
        long commandBufferWatermark,
        int commandBufferCapacity,
        long commandBufferTtlMs,
        long missedDispatchRecords,
        long executorDedupHits,
        long executorConnects,
        long logQueueDrops,
        boolean executorOnline,
        long executorProcessed,
        long executorDropped,
        long executorConcurrentSkipped) {
}
