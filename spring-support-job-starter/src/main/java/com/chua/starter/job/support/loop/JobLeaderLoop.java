package com.chua.starter.job.support.loop;

import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leader 单秒扫描循环 (SCHEDULER / SINGLE 角色专用)。
 *
 * <p>每秒调用 {@link JobRepository#findDue(long, long, int)} 拉取到期任务,
 * 通过 {@link JobSyncBus#publish(String, String, Object)} 发出 cmd 事件:</p>
 * <ul>
 *   <li>SCHEDULER: bus 上的 cmd 事件被 {@code SchedulerSyncBinder} 转发给所有 Executor</li>
 *   <li>SINGLE:    bus 上的 cmd 事件被本地 {@code JobExecutor} 直接消费</li>
 * </ul>
 *
 * <p>非 Leader 节点不会创建本循环, 数据库查询压力归零。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class JobLeaderLoop {

    /**
     * 默认预读批次大小
     */
    public static final int DEFAULT_PRE_READ = 200;

    /**
     * 配置属性
     */
    private final JobProperties properties;

    /**
     * Leader 选举器
     */
    private final LeaderElector leaderElector;

    /**
     * 当前节点标识
     */
    private final String nodeId;

    /**
     * 任务仓库
     */
    private final JobRepository repository;

    /**
     * 事件总线
     */
    private final JobSyncBus bus;

    /**
     * 运行标记
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 工作线程
     */
    private volatile Thread workerThread;

    /**
     * 启动单秒扫描循环。
     */
    @PostConstruct
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        workerThread = new Thread(this::loop, "job-leader-loop");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * 停止循环。
     */
    @PreDestroy
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Thread t = workerThread;
        if (t != null) {
            t.interrupt();
        }
    }

    /**
     * 主循环, sleep 对齐秒级周期。
     */
    private void loop() {
        long intervalMs = Math.max(properties.getCluster().getScanIntervalMs(), 100L);
        long preReadMs = properties.getCluster().getPreReadMs();
        log.info("[job] Leader 循环已启动 nodeId={} interval={}ms preRead={}ms",
                nodeId, intervalMs, preReadMs);
        while (running.get()) {
            try {
                tick(preReadMs);
            } catch (Throwable ex) {
                log.error("[job] Leader 循环异常", ex);
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("[job] Leader 循环已停止 nodeId={}", nodeId);
    }

    /**
     * 单拍轮询, 推送到总线。
     *
     * @param preReadMs 预读时间窗
     */
    private void tick(long preReadMs) {
        if (!leaderElector.isLeader()) {
            log.warn("[job] tick 跳过 (非 Leader) nodeId={}", nodeId);
            return;
        }
        long now = System.currentTimeMillis();
        List<JobDefinition> due = repository.findDue(now, preReadMs, DEFAULT_PRE_READ);
        log.info("[job] tick leader=true due.size={} now={}", due.size(), now);
        if (due.isEmpty()) {
            return;
        }
        log.debug("[job] Leader 单秒扫描发现 {} 个到期任务", due.size());
        for (JobDefinition definition : due) {
            bus.publish(JobSyncTopics.TASK_COMMAND, nodeId, definition.getName());
        }
    }
}
