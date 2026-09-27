package com.chua.starter.job.support.sync;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogSink;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 调度中心侧同步绑定器 (角色=调度器)。
 *
 * <p>持有仓库公共 {@link SyncServer}, 完成:</p>
 * <ul>
 *   <li>本地 {@code JobSyncBus} 的 {@link JobSyncTopics#TASK_COMMAND} 事件 → 通过 {@code syncServer.send}
 * 路由到 <b>单个</b> 在线 执行器 (粘性轮询), 实现多 执行器 HA 去重, 避免每个执行器都触发任务</li>
 *   <li>subscribe {@link JobSyncTopics#TASK_LOG} → 写入 {@link JobLogSink} (默认 stdout)</li>
 *   <li>subscribe {@link JobSyncTopics#REGISTER} → 仅打日志</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class SchedulerSyncBinder {

    /**
     * 同步服务端 SPI Key
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
     * 执行器上报的处理器清单（用于校正"未注册"判定）
     */
    private final RemoteHandlerRegistry remoteHandlerRegistry;

    /**
     * 脚本正文同步服务（执行器上线后全量推送，覆盖执行器重启丢缓存场景）
     */
    private final ObjectProvider<GlueSyncService> glueSyncServiceProvider;

    /**
     * CMD 转发器 (持有, 用于注销)
     */
    private BiConsumer<String, Object> cmdForwarder;

    /**
     * 日志 接收器
     */
    private SyncMessageHandler logHandler;

    /**
     * 粘性轮询计数, 累加后取模 客户端 数, 保证每个 due 任务落在同一 执行器 直到 客户端 列表变化。
     */
    private final AtomicLong stickyCounter = new AtomicLong(0L);

    /**
     * 无执行器丢单记录的节流表: 任务键 → 上次记录时刻（毫秒），避免执行器长时间离线刷爆日志表
     */
    private final Map<String, Long> missedDispatchLoggedAt = new ConcurrentHashMap<>();

    /**
     * 同一任务丢单记录的节流窗口（毫秒）
     */
    private static final long MISSED_LOG_THROTTLE_MS = 60_000L;

    /**
     * 启动绑定。
     */
    @PostConstruct
    public void start() {
 // 本地 bus 上的 cmd 推给 <b>一个</b> 执行器 (粘性轮询)
        cmdForwarder = (source, payload) -> {
            try {
                List<String> clients = syncServer.getConnectedClients();
                log.info("[job] getConnectedClients()={} size={}", clients, clients == null ? 0 : clients.size());
                if (clients == null || clients.isEmpty()) {
 // 0 在线执行器: 广播兜底（可能有未及时上报的客户端），并把丢单落到日志表，避免静默丢失
                    syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
                    log.warn("[job] 无在线执行器, cmd 可能被丢弃 topic={} payload={}",
                            JobSyncTopics.TASK_COMMAND, payload);
                    recordMissedDispatch(payload);
                    return;
                }
                if (clients.size() == 1) {
 // 单 执行器 场景: 直接 broadcast 兼容 stale 通道 entry (规避 tcp同步服务端
 // 发送 单播路径在 客户端 已断但 映射 未清理时静默丢帧的 pre-existing bug)
                    syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
                    log.info("[job] cmd 已发 (单 client broadcast 兼容) topic={} clientId={} payload={}",
                            JobSyncTopics.TASK_COMMAND, clients.getFirst(), payload);
                    return;
                }
 // 多 执行器 场景: sticky 单播 (#1 HA 去重的核心)
 // 注意: 同步服务端.发送 签名固定为 (客户端id, topic, 消息), 顺序不可颠倒,
 // 否则粘性单播会把 客户端标识 与 topic 互换, 造成派发全部落空。
                long idx = Math.floorMod(stickyCounter.getAndIncrement(), (long) clients.size());
                String clientId = clients.get((int) idx);
                try {
                    syncServer.send(clientId, JobSyncTopics.TASK_COMMAND, payload);
                    log.info("[job] cmd 已发 (sticky 单播) topic={} clientId={} idx={}/{} payload={}",
                            JobSyncTopics.TASK_COMMAND, clientId, idx, clients.size(), payload);
                } catch (Throwable ex) {
                    log.warn("[job] cmd send 单播异常 cause={}", ex.getMessage(), ex);
                }
            } catch (Throwable ex) {
                log.warn("[job] send 失败 cause={}", ex.getMessage());
            }
        };
        bus.subscribe(JobSyncTopics.TASK_COMMAND, cmdForwarder);

 // 接收 执行器 上行的 任务-日志
        logHandler = (topic, payload) -> {
 // wire 上收到的是 字符串 (Netty 行协议), 用 作业日志事件.从json 反序列化
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
            /**
             * on消息
            */
            public void onMessage(String clientId, String topic, Object message) {
                if (JobSyncTopics.TASK_LOG.equals(topic)) {
                    log.info("[job] SCHEDULER 收到上行 TASK_LOG clientId={} messageType={}",
                            clientId, message == null ? "null" : message.getClass().getSimpleName());
                    logHandler.handle(topic, message);
                } else if (JobSyncTopics.REGISTER.equals(topic)) {
                    log.info("[job] executor 上线 clientId={} payload={}", clientId, message);
                    remoteHandlerRegistry.register(clientId,
                            JobSyncTopics.parseRegister(message == null ? null : String.valueOf(message)));
                    // 执行器刚上线/重连：异步全量推送脚本正文（读取台账 + 广播，放虚拟线程避免阻塞同步读线程）
                    GlueSyncService glueSyncService = glueSyncServiceProvider.getIfAvailable();
                    if (glueSyncService != null) {
                        Thread.ofVirtual().name("job-glue-push-" + clientId)
                                .start(glueSyncService::pushAll);
                    }
                } else if (JobSyncTopics.HANDLERS.equals(topic)) {
                    java.util.List<String> names =
                            JobSyncTopics.parseHandlers(message == null ? null : String.valueOf(message));
                    log.info("[job] executor 处理器清单更新 clientId={} count={}", clientId, names.size());
                    remoteHandlerRegistry.updateHandlers(clientId, names);
                }
            }

            @Override
            public void onClientDisconnected(String clientId) {
                log.info("[job] executor 下线 clientId={}", clientId);
                remoteHandlerRegistry.remove(clientId);
            }
        });

        if ("com.chua.common.support.network.server.AbstractServer".equals(
                syncServer.getClass().getSuperclass().getName())) {
            try {
                ReflectUtils.invoke(syncServer, "start", void.class);
                log.info("[job] SyncServer 已启动, 等待 Executor 连接");
            } catch (Throwable ex) {
 // 父类没有 启动 方法 (可能版本不匹配), 通过 发布 lazy 启动
                log.debug("[job] SyncServer start 失败 cause={}", ex.getMessage());
            }
        }
        log.info("[job] SCHEDULER 同步绑定器已启动 nodeId={}", nodeId);
    }

    /**
     * 无执行器时的丢单记录（按任务节流）。
     *
     * <p>执行器长时间离线时，每一拍都写日志会刷爆日志表，因此同一任务 60s 内只记一条
     * SKIPPED，理由写明"无在线执行器"——作业没跑是事实，不该从日志里消失。</p>
     *
     * @param payload 派发负载
     */
    private void recordMissedDispatch(Object payload) {
        String raw = payload == null ? "" : String.valueOf(payload);
        String jobName = JobSyncTopics.commandJobName(raw);
        long nowMs = System.currentTimeMillis();
        Long last = missedDispatchLoggedAt.put(jobName, nowMs);
        if (last != null && nowMs - last < MISSED_LOG_THROTTLE_MS) {
            return;
        }
        try {
            logSink.write(JobLogEvent.skipped(jobName, "无在线执行器，命令未派发", nodeId));
        } catch (Throwable ex) {
            log.warn("[job] 丢单记录写入失败 job={} cause={}", jobName, ex.getMessage());
        }
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
