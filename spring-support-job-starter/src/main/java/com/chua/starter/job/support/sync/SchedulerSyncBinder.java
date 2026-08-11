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

import java.util.function.BiConsumer;

/**
 * 调度中心侧同步绑定器 (role=SCHEDULER)。
 *
 * <p>持有仓库公共 {@link SyncServer}, 完成:</p>
 * <ul>
 *   <li>本地 {@code JobSyncBus} 的 {@link JobSyncTopics#TASK_COMMAND} 事件 → publish 推给所有 Executor</li>
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
     * 启动绑定。
     */
    @PostConstruct
    public void start() {
        // 本地 bus 上的 cmd 推到所有 executor
        cmdForwarder = (source, payload) -> {
            try {
                syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
            } catch (Throwable ex) {
                log.warn("[job] publish 失败 cause={}", ex.getMessage());
            }
        };
        bus.subscribe(JobSyncTopics.TASK_COMMAND, cmdForwarder);

        // 接收 executor 上行的 task-log
        logHandler = (topic, payload) -> {
            if (!(payload instanceof JobLogEvent)) {
                return;
            }
            try {
                logSink.write((JobLogEvent) payload);
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
