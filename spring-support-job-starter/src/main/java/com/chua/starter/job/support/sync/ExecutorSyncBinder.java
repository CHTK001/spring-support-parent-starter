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
import java.util.concurrent.atomic.AtomicLong;
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
     * 最近一次 connect/register 尝试时间戳 (毫秒), 用于周期性心跳式重连
     */
    private final AtomicLong lastReconnectAttemptMs = new AtomicLong(0L);

    /**
     * 已注册后的心跳重连间隔 (毫秒), 在 NettyTcpSyncClient.isConnected()
     * 永远返回 true 的 pre-existing bug 下作为唯一兜底
     */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    /**
     * 启动绑定。
     */
    @PostConstruct
    public void start() {
        cmdHandler = (topic, payload) -> {
            // 简单诊断, 记录 EXECUTOR 端 wire 收到的事件
            log.info("[job] EXECUTOR wire-recv topic={} payload={}", topic, payload);
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

        // 监听 SyncFlowListener.onStop / onError, 当 channel 被对端关闭时立即重置 registered
        //   (pre-existing: Netty client ChannelHandler 没实现 channelInactive, 不会自动通知 listener)
        syncClient.addListener(new com.chua.common.support.network.sync.SyncFlowListener() {
            @Override
            public void onStop() {
                log.warn("[job] SyncClient 收到 onStop, 重置 registered 标志, 守护线程将自动重连");
                registered.set(false);
            }

            @Override
            public void onError(String clientId, Throwable ex) {
                log.warn("[job] SyncClient 收到 onError clientId={} cause={}, 重置 registered",
                        clientId, ex.getMessage());
                registered.set(false);
            }
        });

        // 同步尝试一次, 失败后由守护线程周期重试
        tryConnectAndRegister();

        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-executor-reconnect");
            t.setDaemon(true);
            return t;
        });
        reconnectExecutor.scheduleWithFixedDelay(() -> {
            // 周期心跳式重连策略:
            //   - 已 registered 时每 HEARTBEAT_INTERVAL_MS 调一次 syncClient.disconnect()+connect() 强制重建连接
            //     解决 NettyTcpSyncClient 在对端 (scheduler) 重启后因 channel.close 没通知 listener 而永久失联的问题
            //   - 期间任何 SyncFlowListener.onStop / onError 触发 registered=false 时, 立即进入 tryConnectAndRegister 分支
            //   - 未 registered 时直接走 tryConnectAndRegister 立即重连 (5s 周期)
            long lastTryMs = lastReconnectAttemptMs.get();
            long nowMs = System.currentTimeMillis();
            if (registered.get() && nowMs - lastTryMs < HEARTBEAT_INTERVAL_MS) {
                return;
            }
            lastReconnectAttemptMs.set(nowMs);
            try {
                if (registered.get()) {
                    // 已注册但到心跳周期: 主动 disconnect 触发 onStop → listener 重置 registered → 下次 tick 重建
                    log.info("[job] 心跳触发 EXECUTOR 主动重连 (scheduler 端可能已重启)");
                    syncClient.disconnect();
                    registered.set(false);
                }
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
