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
 * Leader 单秒扫描循环 (调度器 / 单个 角色专用)。
 *
 * <p>每秒调用 {@link JobRepository#findDue(long, long, int)} 拉取到期任务,
 * 通过 {@link JobSyncBus#publish(String, String, Object)} 发出 CMD 事件:</p>
 * <ul>
 *   <li>SCHEDULER: bus 上的 cmd 事件被 {@code SchedulerSyncBinder} 转发给所有 Executor</li>
 *   <li>SINGLE:    bus 上的 cmd 事件被本地 {@code JobExecutor} 直接消费</li>
 * </ul>
 *
 * <p>非 Leader 节点不会创建本循环, 数据库查询压力归零。</p>
 *
 * @since 4.0.0.42
 * @author CH
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
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
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
        // [P3C A20 豁免]
        long intervalMs = Math.max(properties.getCluster().getScanIntervalMs(), 100L);
        long preReadMs = properties.getCluster().getPreReadMs();
        log.info("[job] Leader 循环已启动 nodeId={} interval={}ms preRead={}ms",
                nodeId, intervalMs, preReadMs);
        while (running.get()) {
            try {
                /**
                 * tick。
                 * @param preReadMs pre读取ms
                 */
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
 // Follower 每秒都走到这里, 是集群常态而非异常; 降级由 LeaderElector 打 INFO, 抢占异常由解析器打 ERROR
            log.debug("[job] tick 跳过 (非 Leader) nodeId={}", nodeId);
            return;
        }
        long now = System.currentTimeMillis();
        List<JobDefinition> due = repository.findDue(now, preReadMs, DEFAULT_PRE_READ,
                properties.getCluster().getGroup());
        if (due.isEmpty()) {
            return;
        }
        log.debug("[job] Leader 单秒扫描发现 {} 个到期任务", due.size());
        for (JobDefinition definition : due) {
            // 分布式定时：正文由 GlueSyncService 在保存/执行器上线时推送，命令只带版本号；
            // 单机没有推送通道，定时命令回带正文，否则执行器缓存里永远没有脚本可跑。
            String glueSource = properties.isSingle() ? definition.getGlueSource() : null;
            bus.publish(JobSyncTopics.TASK_COMMAND, nodeId,
                    JobSyncTopics.command(definition.getName(), false,
                            definition.getFailRetry(), definition.getRetryIntervalSec(),
                            definition.getExecuteTimeoutSec(),
                            definition.getGlueType(), glueSource,
                            definition.getGlueUpdatetimeMs(),
                            definition.getRetryCallbackBean(), definition.getExceptionCallbackBean(),
                            definition.getAlarmEmail(), definition.getAlarmType(), definition.getExecuteParam(),
                            // 并发策略随命令下发, 由执行器据此决定"上一轮未结束"时跳过还是放行
                            definition.getConcurrentPolicy()));
        }
    }
}
