package com.chua.starter.job.support.scheduler;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务触发线程池助手
 * <p>
 * 管理快速和慢速两个线程池，根据任务执行时间自动分流。
 * 执行时间超过500ms的任务会被标记为慢任务，超过10次后会被分配到慢速线程池执行。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
public class JobTriggerPoolHelper {

    /**
     * 快速线程池，用于执行正常任务
     */
    private ThreadPoolExecutor fastTriggerPool = null;

    /**
     * 慢速线程池，用于执行超时任务
     */
    private ThreadPoolExecutor slowTriggerPool = null;

    /**
     * 启动线程池
     */
    public void start() {
        fastTriggerPool = new ThreadPoolExecutor(
                10,
                JobConfig.getInstance().getTriggerPoolFastMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> new Thread(r, "job, admin JobTriggerPoolHelper-fastTriggerPool-" + r.hashCode()));

        slowTriggerPool = new ThreadPoolExecutor(
                10,
                JobConfig.getInstance().getTriggerPoolSlowMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                r -> new Thread(r, "job, admin JobTriggerPoolHelper-slowTriggerPool-" + r.hashCode()));
    }

    /**
     * 停止线程池
     */
    public void stop() {
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
        log.info(">>>>>>>>>>> 任务触发线程池已关闭");
    }

    /**
     * 分钟时间戳，用于每分钟重置超时计数
     */
    private volatile long minTim = System.currentTimeMillis() / 60000;

    /**
     * 任务超时计数器
     * key: 任务ID
     * value: 超时次数
     */
    private final ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();

    /**
     * 添加触发器
     *
     * @param jobId                 作业ID
     * @param triggerType           触发类型
     * @param failRetryCount        失败重试次数
     * @param executorShardingParam 执行器分片参数
     * @param executorParam         执行器参数
     */
    public void addTrigger(final int jobId,
                           final TriggerTypeEnum triggerType,
                           final int failRetryCount,
                           final String executorShardingParam,
                           final String executorParam) {

        // 选择线程池
        ThreadPoolExecutor triggerPool = fastTriggerPool;
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);
        // 如果作业超时次数超过10次，则切换到慢速线程池
        if (jobTimeoutCount != null && jobTimeoutCount.get() > 10) {
            triggerPool = slowTriggerPool;
        }

        // 触发执行
        triggerPool.execute(() -> {

            long start = System.currentTimeMillis();

            try {
                // 执行触发逻辑
                LocalJobTrigger.trigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam);
            } catch (Exception e) {
                log.error(">>>>>>>>>>> 任务触发执行异常, jobId={}, 错误={}", jobId, e.getMessage(), e);
            } finally {

                // 检查并更新超时计数Map
                long minTimNow = System.currentTimeMillis() / 60000;
                // 每分钟更新一次超时计数Map
                if (minTim != minTimNow) {
                    minTim = minTimNow;
                    jobTimeoutCountMap.clear();
                }

                // 计算执行耗时并更新超时计数
                long cost = System.currentTimeMillis() - start;
                // 执行时间超过500ms认为是超时
                if (cost > 500) {
                    AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                    if (timeoutCount != null) {
                        timeoutCount.incrementAndGet();
                    }
                }

            }

        });
    }

    // ---------------------- helper ----------------------

    private static final JobTriggerPoolHelper helper = new JobTriggerPoolHelper();

    public static void toStart() {
        helper.start();
    }

    public static void toStop() {
        helper.stop();
    }

    /**
     * 触发指定作业的执行。
     *
     * @param jobId                 作业ID
     * @param triggerType           触发类型
     * @param failRetryCount        失败重试次数
     * @param executorShardingParam 执行器分片参数
     * @param executorParam         执行器参数
     */
    public static void trigger(int jobId, TriggerTypeEnum triggerType, int failRetryCount, String executorShardingParam, String executorParam) {
        helper.addTrigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam);
    }

}
