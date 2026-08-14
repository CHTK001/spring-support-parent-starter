package com.chua.starter.job.support.sync;

import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogSink;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 调度中心侧同步绑定器 (role=SCHEDULER)。
 *
 * <p>持有仓库公共 {@link SyncServer}, 完成:</p>
 * <ul>
 *   <li>本地 {@code JobSyncBus} 的 {@link JobSyncTopics#TASK_COMMAND} 事件 → 通过 {@code syncServer.send}
 *       路由到 <b>单个</b> 在线 Executor (粘性轮询), 实现多 Executor HA 去重, 避免每个执行器都触发任务</li>
 *   <li>subscribe {@link JobSyncTopics#TASK_LOG} → 写入 {@link JobLogSink} (默认 stdout)</li>
 *   <li>subscribe {@link JobSyncTopics#REGISTER} → 仅打日志</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class SchedulerSyncBinder {

    /**
     * SyncServer SPI key
     */
    public static final String SPI_SYNC_SERVER = "sync.server";

    /**
     * 调度服务端实例
     */
    private final SyncServer syncServer;

    /**
     * 进程内事件总线
     */
    private final JobSyncBus bus;

    /**
     * 日志落库实现
     */
    private final JobLogSink logSink;

    /**
     * 当前节点标识
     */
    private final String nodeId;

    /**
     * cmd 转发器 (持有, 用于注销)
     */
    private BiConsumer<String, Object> cmdForwarder;

    /**
     * log 接收器
     */
    private SyncMessageHandler logHandler;

    /**
     * 粘性轮询计数, 累加后取模 client 数, 保证每个 due 任务落在同一 executor 直到 client 列表变化。
     */
    private final AtomicLong stickyCounter = new AtomicLong(0L);

    /**
     * 启动绑定。
     */
    @PostConstruct
    public void start() {
        // 本地 bus 上的 cmd 推给 <b>一个</b> executor (粘性轮询)
        cmdForwarder = (source, payload) -> {
            try {
                List<String> clients = syncServer.getConnectedClients();
                if (clients == null || clients.isEmpty()) {
                    // 0 在线 executor: 退化为 broadcast (历史行为, 等待下次 tick)
                    syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
                    log.debug("[job] 无在线 executor, broadcast fallback payload={}", payload);
                    return;
                }
                long idx = Math.floorMod(stickyCounter.getAndIncrement(), (long) clients.size());
                String clientId = clients.get((int) idx);
                syncServer.send(JobSyncTopics.TASK_COMMAND, clientId, payload);
                log.info("[job] cmd 路由 clientId={} idx={}/{} payload={}",
                        clientId, idx, clients.size(), payload);
            } catch (Throwable ex) {
                log.warn("[job] send 失败 cause={}", ex.getMessage());
            }
        };
        bus.subscribe(JobSyncTopics.TASK_COMMAND, cmdForwarder);

        // 接收 executor 上行的 task-log
        logHandler = (topic, payload) -> {
            // wire 上收到的是 String (Netty 行协议), 用 JobLogEvent.fromJson 反序列化
            JobLogEvent event = null;
            if (payload instanceof JobLogEvent) {
                event = (JobLogEvent) payload;
            } else if (payload instanceof String) {
                event = JobLogEvent.fromJson((String) payload);
            }
            if (event == null) {
                return;
            }
            try {
                logSink.write(event);
            } catch (Throwable ex) {
                log.warn("[job] logSink 写入失败 cause={}", ex.getMessage(), ex);
            }
        };
        syncServer.addListener(new SyncServerListener() {
            @Override
            public void onMessage(String clientId, String topic, Object message) {
                if (JobSyncTopics.TASK_LOG.equals(topic)) {
                    logHandler.handle(topic, message);
                } else if (JobSyncTopics.REGISTER.equals(topic)) {
                    log.info("[job] executor 上线 clientId={} payload={}", clientId, message);
                }
            }
        });

        if ("com.chua.common.support.network.server.AbstractServer".equals(
                syncServer.getClass().getSuperclass().getName())) {
            try {
                syncServer.getClass().getSuperclass().getMethod("start").invoke(syncServer);
                log.info("[job] SyncServer 已启动, 等待 Executor 连接");
            } catch (NoSuchMethodException nsme) {
                // 父类没有 start 方法 (可能版本不匹配), 通过 publish lazy start
            } catch (Throwable ex) {
                log.warn("[job] SyncServer start 失败 cause={}", ex.getMessage());
            }
        }
        log.info("[job] SCHEDULER 同步绑定器已启动 nodeId={}", nodeId);
    }

    /**
     * 关闭绑定。
     */
    @PreDestroy
    public void shutdown() {
        if (cmdForwarder != null) {
            bus.unsubscribe(JobSyncTopics.TASK_COMMAND, cmdForwarder);
        }
        log.info("[job] SCHEDULER 同步绑定器已关闭 nodeId={}", nodeId);
    }
}
