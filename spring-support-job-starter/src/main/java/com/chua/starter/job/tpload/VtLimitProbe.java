package com.chua.starter.job.tpload;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本机虚拟线程承载能力探针: "同时能运行多少任务"分两类回答。
 *
 * <ul>
 *   <li>阻塞型任务 (sleep/IO): 虚拟线程 sleep 时让出 carrier, 可挂起的并发数由内存决定;</li>
 *   <li>CPU 密集型任务: 虚拟线程无处让位, 真实并行上限 = 物理/逻辑核心数。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class VtLimitProbe {

    /**
     * 阻塞型: 并发提交任务量
     */
    private static final int BLOCK_TASKS = 20000;

    /**
     * 阻塞型: 单任务 sleep 时长
     */
    private static final long SLEEP_MS = 500L;

    /**
     * CPU 型: 单任务忙循环基准工作量
     */
    private static final int CPU_WORK = 3_000_000;

    /**
     * CPU 型: 提交任务量
     */
    private static final int CPU_TASKS = 10000;

    /**
     * 运行阻塞型场景。
     */
    private static void runBlock() throws InterruptedException {
        ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch done = new CountDownLatch(BLOCK_TASKS);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger activeMax = new AtomicInteger(0);

        long start = System.nanoTime();
        for (int i = 0; i < BLOCK_TASKS; i++) {
            vt.execute(() -> {
                int cur = active.incrementAndGet();
                activeMax.accumulateAndGet(cur, Math::max);
                try {
                    Thread.sleep(SLEEP_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                    done.countDown();
                }
            });
        }
        done.await(60, TimeUnit.SECONDS);
        long costMs = (System.nanoTime() - start) / 1_000_000;
        vt.shutdown();
        vt.awaitTermination(5, TimeUnit.SECONDS);

        log.info("[probe][阻塞型] {} 件 x {}ms sleep 总耗时={}ms 并发峰值={} 完成吞吐={} 件/s",
                BLOCK_TASKS, SLEEP_MS, costMs, activeMax.get(),
                Math.round(BLOCK_TASKS * 1000.0 / Math.max(1, costMs)));
        log.info("[probe] BLOCK_RESULT tasks={} workMs={} activeMax={} costMs={}",
                BLOCK_TASKS, SLEEP_MS, activeMax.get(), costMs);
    }

    /**
     * 运行 CPU 密集型场景。
     */
    private static void runCpu() throws InterruptedException {
        ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch done = new CountDownLatch(CPU_TASKS);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger activeMax = new AtomicInteger(0);
        AtomicLong sum = new AtomicLong(0);

        long start = System.nanoTime();
        for (int i = 0; i < CPU_TASKS; i++) {
            vt.execute(() -> {
                int cur = active.incrementAndGet();
                activeMax.accumulateAndGet(cur, Math::max);
                long s = 0;
                for (int j = 0; j < CPU_WORK; j++) {
                    s += (j & 1);
                }
                sum.addAndGet(s);
                active.decrementAndGet();
                done.countDown();
            });
        }
        done.await(120, TimeUnit.SECONDS);
        long costMs = (System.nanoTime() - start) / 1_000_000;
        vt.shutdown();
        vt.awaitTermination(5, TimeUnit.SECONDS);

        int cores = Runtime.getRuntime().availableProcessors();
        log.info("[probe][CPU密集] {} 件 x {} 次忙循环 总耗时={}ms 并发峰值={} 可用核心={}",
                CPU_TASKS, CPU_WORK, costMs, activeMax.get(), cores);
        log.info("[probe] CPU_RESULT tasks={} work={} activeMax={} costMs={} cores={} sum={}",
                CPU_TASKS, CPU_WORK, activeMax.get(), costMs, cores, sum.get());
    }

    /**
     * 入口。
     *
     * @param args 忽略
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("[probe] 本机可用处理器 = {} 个", Runtime.getRuntime().availableProcessors());
        runBlock();
        runCpu();
    }
}