package com.chua.starter.job.support.cluster;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.SmartLifecycle;
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
 * (CAS 重入实现)：已是 Leader 时等于续约，不是 Leader 时等于接管；抢占失败则释放本地 Leader 状态。</p>
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
public class ClusterElectionHeartbeat implements SmartLifecycle {

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
     * 是否仍在运行。
     *
     * @return 心跳线程在跑返回 true
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 关闭阶段：取最大值使本组件<b>最先</b>停止。
     *
     * <p>关键：{@code Stop} 发生在 Spring 的 {@code stopBeans()} 阶段，<b>早于</b>
     * {@code destroyBeans()}（即早于各 Bean 的 {@code @PreDestroy}）。
     * 而 Redis 选主实现的模板是经 SpringBeanUtils 惰性取的 —— Spring 并不知道
     * "心跳 → Redis 客户端"这条依赖，销毁顺序不受保证，若等 {@code @PreDestroy} 才释放，
     * Redis 客户端往往已经不可用，租约只能等 TTL 过期（表现为优雅重启后一个 ≤租约时长的派发空窗）。</p>
     *
     * @return 最大 phase
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * 启动心跳。
     */
    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long periodSeconds = Math.max(leaseSeconds / HEARTBEAT_DIVISOR, 1L);
// [P3C C1 豁免] 场景化创建线程，需文档化
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
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
    }}