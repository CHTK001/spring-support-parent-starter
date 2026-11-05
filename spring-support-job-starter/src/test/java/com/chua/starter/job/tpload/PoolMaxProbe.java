// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.job.tpload;

import com.chua.starter.job.support.execute.JobExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池上限探针: 验证 thread游泳池执行器 从 核心 弹性扩容到 最大 的真实上限,
 * 以及打满 最大 + 队列满之后 abortpolicy 的拒绝行为。
 *
 * <p>复刻 {@link JobAutoConfiguration#buildExecutor()} 的构造参数
 * (核心/最大/队列 + 名称thread工厂 + abortpolicy), 故意把队列容量调小
 * (队列=10), 一次性突发提交远超处理能力的任务, 观察:</p>
 * <ul>
 *   <li>{@code largestPoolSize} 是否精确等于 max (弹性扩容上限);</li>
 *   <li>thread 峰值、active 峰值;</li>
 *   <li>超过 max 后不再新建线程, AbortPolicy 抛 {@link java.util.concurrent.RejectedExecutionException}。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class PoolMaxProbe {

    /**
     * 核心线程数
     */
    private static final int CORE = 8;

    /**
     * 最大线程数
     */
    private static final int MAX = 32;

    /**
     * 故意缩小队列容量, 使任务量快速压满 核心 + 队列, 推动线程扩到 最大
     */
    private static final int QUEUE = 10;

    /**
     * 单个任务模拟耗时 (毫秒)
     */
    private static final long WORK_MS = 300L;

    /**
     * 突发提交任务总数 (远超 核心+队列+ (最大-核心) 的总在途容量, 必触发拒绝)
     */
    private static final int TASKS = 300;

    /**
     * 已在执行的并发峰值
     */
    private static final AtomicInteger ACTIVE_MAX = new AtomicInteger(0);

    /**
     * 入口。
     *
     * @param args 命令行参数 (忽略)
     */
    public static void main(String[] args) throws InterruptedException {
// [P3C C1 豁免] 场景化创建线程，需文档化
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE, MAX, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE),
                JobExecutor.namedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch done = new CountDownLatch(TASKS);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger active = new AtomicInteger(0);

        for (int i = 0; i < TASKS; i++) {
            try {
                pool.execute(() -> {
                    try {
                        int cur = active.incrementAndGet();
                        ACTIVE_MAX.accumulateAndGet(cur, Math::max);
                        Thread.sleep(WORK_MS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        active.decrementAndGet();
                        done.countDown();
                    }
                });
            } catch (Exception ex) {
                rejected.incrementAndGet();
                if (rejected.get() == 1) {
                    log.warn("[probe] 首次拒绝 cause={}", ex.getMessage());
                }
                done.countDown();
            }
        }

        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        log.info("[probe] 配置 core={} max={} queue={} 突发任务={}", CORE, MAX, QUEUE, TASKS);
        log.info("[probe] 线程峰值 largestPoolSize={} (期望=MAX {})", pool.getLargestPoolSize(), MAX);
        log.info("[probe] 并发峰值 activeMax={} (期望=MAX {})", ACTIVE_MAX.get(), MAX);
        log.info("[probe] 拒绝任务 rejected={} (期望={})", rejected.get(),
                Math.max(0, TASKS - (MAX + QUEUE)));
        log.info("[probe] POOLMAX_RESULT largestPoolSize={} activeMax={} rejected={} core={} max={} queue={}",
                pool.getLargestPoolSize(), ACTIVE_MAX.get(), rejected.get(), CORE, MAX, QUEUE);    }}