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
* 执行器侧同步绑定器 (角色=执行器)。
*
* <p>持有仓库公共 {@link SyncClient}, 完成:</p>
* <ul>
*   <li>subscribe {@link JobSyncTopics#TASK_COMMAND} → 派发到本地 {@link JobSyncBus}</li>
*   <li>本地 {@code JobSyncBus} 的 {@link JobSyncTopics#TASK_LOG} 事件 → send 上行给 Scheduler</li>
*   <li>启动时 send {@link JobSyncTopics#REGISTER} 上报</li>
*   <li>连接失败时由内部 daemon 周期重试, 避免 server 尚不可用时 EXECUTOR 永远失联</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class ExecutorSyncBinder {

    /**
    * 同步客户端 SPI 钥匙, 由仓库公共服务端负责注入
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
    * 任务 CMD 监听器
    */
    private SyncMessageHandler cmdHandler;

    /**
    * 日志 转发器 (持有, 用于注销)
    */
    private BiConsumer<String, Object> logForwarder;

    /**
    * 重连守护调度
    */
    private ScheduledExecutorService reconnectExecutor;

    /**
    * 标记是否已注册 (成功后置 true, 避免重复 日志)
    */
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
    * 最近一次 连接/注册 尝试时间戳 (毫秒), 用于周期性心跳式重连
    */
    private final AtomicLong lastReconnectAttemptMs = new AtomicLong(0L);

    /**
    * 已注册后的心跳重连间隔 (毫秒), 在 tcp同步客户端.是否连接()
    * 永远返回 true 的 pre-existing bug 下作为唯一兜底
    */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    /**
    * 启动绑定。
    */
    @PostConstruct
    public void start() {
        cmdHandler = (topic, payload) -> {
 // 简单诊断, 记录 执行器 端 wire 收到的事件
            log.info("[job] EXECUTOR wire-recv topic={} payload={}", topic, payload);
            bus.publish(topic, nodeId, payload);
        };
        syncClient.subscribe(JobSyncTopics.TASK_COMMAND, cmdHandler);

        logForwarder = (source, payload) -> {
            if (!(payload instanceof JobLogEvent event)) {
                return;
            }
            try {
 // 序列化为 JSON 而非 转为字符串(), 服务端可反序列化为 作业日志事件
                String json = event.toJson();
                syncClient.send(JobSyncTopics.TASK_LOG, json);
                log.info("[job] EXECUTOR 上行 TASK_LOG topic={} job={} status={} jsonSnippet={}",
                        JobSyncTopics.TASK_LOG, event.jobName(), event.status(),
                        json.length() > 120 ? json.substring(0, 120) + "..." : json);
            } catch (Throwable ex) {
                log.warn("[job] 上行 TASK_LOG 失败 cause={}", ex.getMessage());
            }
        };
        bus.subscribe(JobSyncTopics.TASK_LOG, logForwarder);

 // 监听 同步流监听器.on停止 / on错误, 当 通道 被对端关闭时立即重置 注册
 // (pre-existing: Netty 客户端 通道处理器 没实现 通道inactive, 不会自动通知 监听器)
        syncClient.addListener(new com.chua.common.support.network.sync.SyncFlowListener() {
            @Override
            /** On停止 */
            public void onStop() {
                log.warn("[job] SyncClient 收到 onStop, 重置 registered 标志, 守护线程将自动重连");
                registered.set(false);
            }

            @Override
            /** On记录错误 */
            public void onError(String clientId, Throwable ex) {
                log.warn("[job] SyncClient 收到 onError clientId={} cause={}, 重置 registered",
                        clientId, ex.getMessage());
                registered.set(false);
            }
        });

        // 同步尝试一次, 失败后由守护线程周期重试
        /**
            * 尝试连接和注册。
            */
        tryConnectAndRegister();

        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-executor-reconnect");
            t.setDaemon(true);
            return t;
        });
        reconnectExecutor.scheduleWithFixedDelay(() -> {
            // 周期心跳式重连策略:
 // - 已 注册 时每 心跳_间隔_MS 调一次 同步客户端.断开连接()+连接() 强制重建连接
 // 解决 tcp同步客户端 在对端 (调度器) 重启后因连接断开没通知 监听器 而永久失联的问题
 // - 期间任何 同步流监听器.on停止 / on错误 触发 注册=false 时, 立即进入 尝试连接and注册 分支
 // - 未 注册 时直接走 尝试连接and注册 立即重连 (5s 周期)
            long lastTryMs = lastReconnectAttemptMs.get();
            long nowMs = System.currentTimeMillis();
            if (registered.get() && nowMs - lastTryMs < HEARTBEAT_INTERVAL_MS) {
                return;
            }
            lastReconnectAttemptMs.set(nowMs);
            try {
                if (registered.get()) {
 // 已注册但到心跳周期: 主动 断开连接 触发 on停止 → 监听器 重置 注册 → 下次 tick 重建
                    log.info("[job] 心跳触发 EXECUTOR 主动重连 (scheduler 端可能已重启)");
                    syncClient.disconnect();
                    registered.set(false);
                }
                /**
                * 尝试连接和注册。
                */
                tryConnectAndRegister();
            } catch (Throwable ex) {
                log.debug("[job] reconnect 失败 cause={}", ex.getMessage());
            }
        }, 5L, 5L, TimeUnit.SECONDS);
    }

    /**
    * 同步尝试连接 + 注册 上行一次。
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
