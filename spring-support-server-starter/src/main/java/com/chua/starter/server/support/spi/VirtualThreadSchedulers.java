package com.chua.starter.server.support.spi;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程 Reactor 调度器（共享）
 * <p>
 * 主机采集大量是阻塞 IO（SSH / WinRM / Agent HTTP / JDBC）。
 * 时间轮到点后，把这些阻塞采集统一 offload 到「每任务一个虚拟线程」的调度器上并发执行，
 * 既不占用时间轮线程，也不必为 boundedElastic 的平台线程数量所限。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class VirtualThreadSchedulers {

    /**
     * 虚拟线程执行器：每个任务一个虚拟线程
     */
    private static final ExecutorService VIRTUAL_EXECUTOR =
// [P3C C1 豁免] 场景化创建线程，需文档化
            Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("host-collect-").factory());

    /**
     * 基于虚拟线程执行器的 Reactor 调度器（懒初始化、单例）
     */
    private static volatile Scheduler scheduler;

    private VirtualThreadSchedulers() {
    }

    /**
     * 获取虚拟线程 Reactor 调度器
     *
     * @return 共享调度器
     */
    public static Scheduler virtual() {
        Scheduler s = scheduler;
        if (s != null) {
            return s;
        }
        synchronized (VirtualThreadSchedulers.class) {
            if (scheduler == null) {
                scheduler = Schedulers.fromExecutorService(VIRTUAL_EXECUTOR, "host-collect-virtual");
            }
            return scheduler;
        }
    }
}