package com.chua.starter.monitor.server.job.trigger;

import com.chua.starter.monitor.server.job.JobConfig;
import com.chua.starter.monitor.server.job.TriggerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * job trigger thread pool helper
 *
 * @author xuxueli 2018-07-03 21:08:07
 */
public class JobTriggerPoolHelper {
    private static Logger logger = LoggerFactory.getLogger(JobTriggerPoolHelper.class);


    // ---------------------- trigger pool ----------------------

    // fast/slow thread pool
    private ThreadPoolExecutor fastTriggerPool = null;
    private ThreadPoolExecutor slowTriggerPool = null;

    public void start(){
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
                new LinkedBlockingQueue<Runnable>(2000),
                r -> new Thread(r, "job, admin JobTriggerPoolHelper-slowTriggerPool-" + r.hashCode()));
    }


    public void stop() {
        //triggerPool.shutdown();
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
        logger.info(">>>>>>>>> job trigger thread pool shutdown success.");
    }


    // job timeout count
    private volatile long minTim = System.currentTimeMillis()/60000;     // ms > min
    private volatile ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();


    /**
     * 添加触发器
     *
     * @param jobId 作业ID
     * @param triggerType 触发类型
     * @param failRetryCount 失败重试次数
     * @param executorShardingParam 执行器分片参数
     * @param executorParam 执行器参数
     */
    public void addTrigger(final int jobId,
                           final TriggerTypeEnum triggerType,
                           final int failRetryCount,
                           final String executorShardingParam,
                           final String executorParam) {

        // 选择线程池
        ThreadPoolExecutor triggerPool_ = fastTriggerPool;
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);
        if (jobTimeoutCount!=null && jobTimeoutCount.get() > 10) {      // 如果作业超时次数超过10次，则切换到慢速线程池
            triggerPool_ = slowTriggerPool;
        }

        // 触发执行
        triggerPool_.execute(() -> {

            long start = System.currentTimeMillis();

            try {
                // 执行触发逻辑
                XxlJobTrigger.trigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {

                // 检查并更新超时计数Map
                long minTim_now = System.currentTimeMillis()/60000;
                if (minTim != minTim_now) { // 每分钟更新一次超时计数Map
                    minTim = minTim_now;
                    jobTimeoutCountMap.clear();
                }

                // 计算执行耗时并更新超时计数
                long cost = System.currentTimeMillis()-start;
                if (cost > 500) {       // 执行时间超过500ms认为是超时
                    AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                    if (timeoutCount != null) {
                        timeoutCount.incrementAndGet();
                    }
                }

            }

        });
    }




    // ---------------------- helper ----------------------

    private static JobTriggerPoolHelper helper = new JobTriggerPoolHelper();

    public static void toStart() {
        helper.start();
    }
    public static void toStop() {
        helper.stop();
    }

    /**
     * 触发指定作业的执行。
     *
     * @param jobId 作业ID，用于标识需要触发的作业。
     * @param triggerType 触发类型，定义了作业是如何被触发的（如手动、定时等）。
     * @param failRetryCount 失败重试次数，定义了作业在失败时的重试次数。
     * @param executorShardingParam 执行器分片参数，用于分布式执行时的分片标识。
     * @param executorParam 执行器参数，传递给执行器的额外参数。
     */
    public static void trigger(int jobId, TriggerTypeEnum triggerType, int failRetryCount, String executorShardingParam, String executorParam) {
        helper.addTrigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam);
        // 通过助手类向系统添加一个触发请求，以启动指定的作业执行。
    }

}
