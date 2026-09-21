package com.chua.starter.job.support.cluster;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leader 续约心跳守护线程。
 *
 * <p>每 {@code leaseSeconds/3} 秒由守护线程主动调用 {@link LeaderElectionResolver#tryAcquire}
 * (CAS 重入实现)：已是 Leader 时等于续约，不是 Leader 时等于接管；抢占失败则释放本地 铅 状态。</p>
 *
 * <h3>行为</h3>
 * <ul>
 *   <li>启动时初始化 {@link ScheduledExecutorService} 单线程;</li>
 *   <li>{@code @PreDestroy} 优雅关闭, 释放 Leader 权。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterElectionHeartbeat {

    /**
     * 心跳周期占租约比例 (心跳 = lease / 3)
     */
    public static final int HEARTBEAT_DIVISOR = 3;

    /**
     * 心跳执行器
     */
    private final LeaderElector leaderElector;

    /**
     * Leader 选举解析器
     */
    private final LeaderElectionResolver resolver;

    /**
     * 当前命名空间
     */
    private final String clusterGroup;

    /**
     * 租约时长 (秒)
     */
    private final int leaseSeconds;

    /**
     * 心跳执行器
     */
    private ScheduledExecutorService executor;

    /**
     * 运行标记
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 启动心跳。
     */
    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long periodSeconds = Math.max(leaseSeconds / HEARTBEAT_DIVISOR, 1L);
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "job-cluster-heartbeat");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        log.info("[job] Leader 心跳已启动 group={} lease={}s period={}s",
                clusterGroup, leaseSeconds, periodSeconds);
    }

    /**
     * 停止心跳。
     */
    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        try {
            resolver.release(clusterGroup, leaderElector.nodeId());
        } catch (Exception ex) {
            log.warn("[job] 释放 Leader 异常 cause={}", ex.getMessage());
        }
        leaderElector.release();
        log.info("[job] Leader 心跳已停止 group={}", clusterGroup);
    }

    /**
     * 单拍抢占/续约。
     *
     * <p>不是 Leader 时同样要抢：进程被强杀不会走 {@link #stop()} 释放租约，接替者启动时
     * 前任的租约往往还没到期，这一拍抢不到若就此收手，就再也没有节点派发任务了。</p>
     */
    private void tick() {
        boolean ok = resolver.tryAcquire(clusterGroup, leaderElector.nodeId(), leaseSeconds);
        if (ok) {
            if (!leaderElector.isLeader()) {
                leaderElector.markLeader(clusterGroup);
            }
            return;
        }
        if (leaderElector.isLeader()) {
            log.warn("[job] Leader 续约失败, 放弃本地 Leader 状态");
            leaderElector.release();
        }
    }
}
