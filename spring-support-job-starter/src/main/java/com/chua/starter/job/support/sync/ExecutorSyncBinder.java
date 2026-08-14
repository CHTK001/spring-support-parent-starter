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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 执行器侧同步绑定器 (role=EXECUTOR)。
 *
 * <p>持有仓库公共 {@link SyncClient}, 完成:</p>
 * <ul>
 *   <li>subscribe {@link JobSyncTopics#TASK_COMMAND} → 派发到本地 {@link JobSyncBus}</li>
 *   <li>本地 {@code JobSyncBus} 的 {@link JobSyncTopics#TASK_LOG} 事件 → send 上行给 Scheduler</li>
 *   <li>启动时 send {@link JobSyncTopics#REGISTER} 上报</li>
 *   <li>连接失败时由内部 daemon 周期重试, 避免 server 尚不可用时 EXECUTOR 永远失联</li>
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
     * 重连守护调度
     */
    private ScheduledExecutorService reconnectExecutor;

    /**
     * 标记是否已注册 (成功后置 true, 避免重复 log)
     */
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * 启动绑定。
     */
    @PostConstruct
    public void start() {
        cmdHandler = (topic, payload) -> {
            log.info("[job] EXECUTOR cmdHandler 收到 topic={} payload={} payloadClass={}",
                    topic, payload, payload == null ? "null" : payload.getClass().getName());
            bus.publish(topic, nodeId, payload);
        };
        syncClient.subscribe(JobSyncTopics.TASK_COMMAND, cmdHandler);

        logForwarder = (source, payload) -> {
            if (!(payload instanceof JobLogEvent)) {
                return;
            }
            try {
                // 序列化为 JSON 而非 toString(), 服务端可反序列化为 JobLogEvent
                String json = ((JobLogEvent) payload).toJson();
                syncClient.send(JobSyncTopics.TASK_LOG, json);
            } catch (Throwable ex) {
                log.warn("[job] 上行 TASK_LOG 失败 cause={}", ex.getMessage());
            }
        };
        bus.subscribe(JobSyncTopics.TASK_LOG, logForwarder);

        // 同步尝试一次, 失败后由守护线程周期重试
        tryConnectAndRegister();

        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-executor-reconnect");
            t.setDaemon(true);
            return t;
        });
        reconnectExecutor.scheduleWithFixedDelay(() -> {
            if (registered.get()) {
                return;
            }
            try {
                tryConnectAndRegister();
            } catch (Throwable ex) {
                log.debug("[job] reconnect 失败 cause={}", ex.getMessage());
            }
        }, 5L, 5L, TimeUnit.SECONDS);
    }

    /**
     * 同步尝试连接 + REGISTER 上行一次。
     */
    private void tryConnectAndRegister() {
        try {
            syncClient.connect();
            syncClient.send(JobSyncTopics.REGISTER, buildRegisterPayload());
            if (registered.compareAndSet(false, true)) {
                log.info("[job] EXECUTOR 已连接 serverUrl={} nodeId={}",
                        properties.getSync().getExecutor().getServerUrl(), nodeId);
            }
        } catch (Exception ex) {
            log.warn("[job] ExecutorSyncBinder 连接失败 cause={}", ex.getMessage());
        }
    }

    /**
     * 关闭绑定。
     */
    @PreDestroy
    public void shutdown() {
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
        }
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
