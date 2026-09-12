package com.chua.starter.job.tpload;

import com.chua.starter.job.support.execute.JobExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平台线程 vs 虚拟线程吞吐对比探针。
 *
 * <p>同一批任务 (N 个, 每件 {@link #WORK_MS} 毫秒, 主要是 Thread.sleep 阻塞型负载),
 * 分别交给:</p>
 * <ul>
 *   <li>平台线程池: core=max=32 + AbortPolicy (与 {@code buildExecutor()} 一致的形态);</li>
 *   <li>虚拟线程执行器: {@link Executors#newVirtualThreadPerTaskExecutor()} 每任务一个虚拟线程。</li>
 * </ul>
 *
 * <p>度量两种执行器跑完全部任务的总耗时 (越小越快) 与吞吐 (件/秒)。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class VtProbe {

    /**
     * 任务总数
     */
    private static final int TASKS = 300;

    /**
     * 单任务模拟耗时 (毫秒)
     */
    private static final long WORK_MS = 500L;

    /**
     * 平台线程池核心/最大线程数
     */
    private static final int PLATFORM_MAX = 32;

    /**
     * 平台线程池队列容量
     */
    private static final int QUEUE = 1000;

    /**
     * 当前活跃数 (用于观测并发峰值)
     */
    private static final AtomicInteger ACTIVE = new AtomicInteger(0);

    /**
     * 运行一组任务并返回耗时。
     *
     * @param executor 执行器
     * @param label    标签
     * @return 总耗时毫秒
     */
    private static long run(ExecutorService executor, String label) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(TASKS);
        AtomicInteger activeMax = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger active = new AtomicInteger(0);

        long start = System.nanoTime();
        for (int i = 0; i < TASKS; i++) {
            try {
                executor.execute(() -> {
                    int cur = active.incrementAndGet();
                    activeMax.accumulateAndGet(cur, Math::max);
                    try {
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
                done.countDown();
            }
        }
        done.await(60, TimeUnit.SECONDS);
        long costMs = (System.nanoTime() - start) / 1_000_000;

        double perSec = TASKS * 1000.0 / Math.max(1, costMs);
        log.info("[probe][{}] {} 件 x {}ms 总耗时={}ms 吞吐={} 件/s 并发峰值={} 拒绝={}",
                label, TASKS, WORK_MS, costMs, Math.round(perSec), activeMax.get(), rejected.get());
        return costMs;
    }

    /**
     * 入口。
     *
     * @param args 忽略
     */
    public static void main(String[] args) throws InterruptedException {
 // 平台线程池: 与 作业auto配置.构建执行器() 同构
        ThreadPoolExecutor platform = new ThreadPoolExecutor(
                PLATFORM_MAX, PLATFORM_MAX, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE),
                JobExecutor.namedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        long platformMs = run(platform, "平台线程 core/max=" + PLATFORM_MAX);
        platform.shutdown();
        platform.awaitTermination(5, TimeUnit.SECONDS);

        // 虚拟线程执行器: 每任务一个虚拟线程
        ExecutorService virtualThread = Executors.newVirtualThreadPerTaskExecutor();
        long vtMs = run(virtualThread, "虚拟线程 per-task");
        virtualThread.shutdown();
        virtualThread.awaitTermination(5, TimeUnit.SECONDS);

        double speedUp = (double) platformMs / Math.max(1, vtMs);
        log.info("[probe] VTPROBE_RESULT platformMs={} vtMs={} speedUp={}x tasks={} workMs={}",
                platformMs, vtMs, Math.round(speedUp * 10) / 10.0, TASKS, WORK_MS);
    }
}