package com.chua.starter.job.support.sync;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.log.JobLogEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 执行器侧同步绑定器 (role=EXECUTOR)。
 *
 * <p>持有仓库公共 {@link SyncClient}, 完成:</p>
 * <ul>
 *   <li>subscribe {@link JobSyncTopics#TASK_COMMAND} → 派发到本地 {@link JobSyncBus}</li>
 *   <li>本地 {@code JobSyncBus} 的 {@link JobSyncTopics#TASK_LOG} 事件 → send 上行给 Scheduler</li>
 *   <li>启动时 send {@link JobSyncTopics#REGISTER} 上报</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class ExecutorSyncBinder {

    /**
     * SyncClient SPI key, 由仓库公共服务端负责注入
     */
    public static final String SPI_SYNC_CLIENT = "sync.client";

    /**
     * 同步客户端实例
     */
    private final SyncClient syncClient;

    /**
     * 进程内事件总线
     */
    private final JobSyncBus bus;

    /**
     * 配置属性
     */
    private final JobProperties properties;

    /**
     * 当前节点标识
     */
    private final String nodeId;

    /**
     * 任务 cmd 监听器
     */
    private SyncMessageHandler cmdHandler;

    /**
     * log 转发器 (持有, 用于注销)
     */
    private BiConsumer<String, Object> logForwarder;

    /**
     * 启动绑定。
     */
    @PostConstruct
    public void start() {
        cmdHandler = (topic, payload) -> bus.publish(topic, nodeId, payload);
        syncClient.subscribe(JobSyncTopics.TASK_COMMAND, cmdHandler);

        logForwarder = (source, payload) -> {
            if (!(payload instanceof JobLogEvent)) {
                return;
            }
            try {
                syncClient.send(JobSyncTopics.TASK_LOG, payload);
            } catch (Throwable ex) {
                log.warn("[job] 上行 TASK_LOG 失败 cause={}", ex.getMessage());
            }
        };
        bus.subscribe(JobSyncTopics.TASK_LOG, logForwarder);

        try {
            syncClient.send(JobSyncTopics.REGISTER, buildRegisterPayload());
            syncClient.connect();
            log.info("[job] EXECUTOR 已连接 serverUrl={} nodeId={}",
                    properties.getSync().getExecutor().getServerUrl(), nodeId);
        } catch (Exception ex) {
            log.warn("[job] ExecutorSyncBinder 启动失败 cause={}", ex.getMessage());
        }
    }

    /**
     * 关闭绑定。
     */
    @PreDestroy
    public void shutdown() {
        if (cmdHandler != null) {
            syncClient.unsubscribe(JobSyncTopics.TASK_COMMAND);
        }
        if (logForwarder != null) {
            bus.unsubscribe(JobSyncTopics.TASK_LOG, logForwarder);
        }
        try {
            syncClient.disconnect();
            syncClient.close();
        } catch (Exception ignored) {
            // 关闭异常忽略
        }
    }

    /**
     * 构建注册上行 payload。
     *
     * @return 注册信息
     */
    private Map<String, Object> buildRegisterPayload() {
        return Map.of(
                "nodeId", nodeId,
                "role", "EXECUTOR",
                "serverUrl", properties.getSync().getExecutor().getServerUrl()
        );
    }
}
