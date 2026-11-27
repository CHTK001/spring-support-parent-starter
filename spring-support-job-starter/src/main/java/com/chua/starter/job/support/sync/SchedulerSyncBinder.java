// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.job.support.sync;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogSink;
import com.chua.starter.job.support.metrics.JobLinkMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import com.chua.common.support.utils.CollectionUtils;

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
     * 近期命令缓冲记录：发布时刻、负载与目标节点（null 表示广播）。
     */
    private record PendingCommand(long publishedAtMs, String payload, String targetNodeId) {
    }

    /**
     * 近期命令缓冲：断线窗口内可能被静默丢弃的命令暂存于此，
     * 执行器重连上线时按 nodeId 定向补偿重放。
     */
    private final ArrayDeque<PendingCommand> recentCommands = new ArrayDeque<>();

    /**
     * nodeId → 最后一次离线时刻（毫秒）。重连后 clientId 会变，按 nodeId 关联断线窗口；
     * 首次上线（无记录）的执行器不参与重放，避免收到上线前的历史命令。
     */
    private final Map<String, Long> lastDisconnectByNode = new ConcurrentHashMap<>();

    /**
     * 近期命令缓冲容量上限（超出淘汰最旧）。
     */
    private static final int RECENT_COMMAND_CAPACITY = 256;

    /**
     * 近期命令缓冲存活时长（毫秒），重放时早于该窗口的命令跳过。
     */
    private static final long RECENT_COMMAND_TTL_MS = 120_000L;

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
                if (CollectionUtils.isEmpty(clients)) {
 // 0 在线执行器: 广播兜底（可能有未及时上报的客户端），并把丢单落到日志表，避免静默丢失
                    syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
                    // 入近期缓冲：执行器重连上线时补偿重放（0 在线时的广播同样可能被错过）
                    rememberCommand(payload, null);
                    log.warn("[job] 无在线执行器, cmd 可能被丢弃 topic={} payload={}",
                            JobSyncTopics.TASK_COMMAND, payload);
                    recordMissedDispatch(payload);
                    return;
                }
                if (clients.size() == 1) {
 // 单 执行器 场景: 直接 broadcast 兼容 stale 通道 entry (规避 tcp同步服务端
 // 发送 单播路径在 客户端 已断但 映射 未清理时静默丢帧的 pre-existing bug)
                    syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
                    // 入近期缓冲（广播语义）：执行器重连上线时补偿重放
                    rememberCommand(payload, null);
                    log.info("[job] cmd 已发 (单 client broadcast 兼容) topic={} clientId={} payload={}",
                            JobSyncTopics.TASK_COMMAND, clients.getFirst(), payload);
                    return;
                }
 // 多 执行器 场景: sticky 单播 (#1 HA 去重的核心)
 // 注意: 同步服务端.发送 签名固定为 (客户端id, topic, 消息), 顺序不可颠倒,
 // 否则粘性单播会把 客户端标识 与 topic 互换, 造成派发全部落空。
                long idx = Math.floorMod(stickyCounter.getAndIncrement(), (long) clients.size());
                String clientId = clients.get((int) idx);
                String targetNode = remoteHandlerRegistry.nodeIdOf(clientId);
                try {
                    syncServer.send(clientId, JobSyncTopics.TASK_COMMAND, payload);
                    rememberCommand(payload, targetNode);
                    log.info("[job] cmd 已发 (sticky 单播) topic={} clientId={} idx={}/{} payload={}",
                            JobSyncTopics.TASK_COMMAND, clientId, idx, clients.size(), payload);
                } catch (Throwable ex) {
                    // 发送异常（对端可能已断开）同样入缓冲，执行器重连后补偿重放
                    rememberCommand(payload, targetNode);
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
             * @param clientId 字符串
             * @param message 对象
             * @param topic 字符串
             * @return 返回值
            */
            public void onMessage(String clientId, String topic, Object message) {
                if (JobSyncTopics.TASK_LOG.equals(topic)) {
                    log.info("[job] SCHEDULER 收到上行 TASK_LOG clientId={} messageType={}",
                            clientId, message == null ? "null" : message.getClass().getSimpleName());
                    logHandler.handle(topic, message);
                } else if (JobSyncTopics.REGISTER.equals(topic)) {
                    log.info("[job] executor 上线 clientId={} payload={}", clientId, message);
                    JobSyncTopics.RegisterInfo info =
                            JobSyncTopics.parseRegister(message == null ? null : String.valueOf(message));
                    remoteHandlerRegistry.register(clientId, info);
                    // 执行器刚上线/重连：异步全量推送脚本正文 + 补偿重放断线窗口内命令
                    // （读台账与重放都可能阻塞，放虚拟线程避免阻塞同步读线程）
                    GlueSyncService glueSyncService = glueSyncServiceProvider.getIfAvailable();
                    String nodeIdForReplay = info.nodeId() == null ? clientId : info.nodeId();
                    Thread.ofVirtual().name("job-online-sync-" + clientId).start(() -> {
                        if (glueSyncService != null) {
                            glueSyncService.pushAll();
                        }
                        replayRecentCommands(clientId, nodeIdForReplay);
                    });
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
                // 记录节点级断线时刻（重连后 clientId 会变，按 nodeId 关联），
                // 重连上线时只重放断线窗口内发布的命令
                String offlineNode = remoteHandlerRegistry.nodeIdOf(clientId);
                if (offlineNode != null) {
                    if (lastDisconnectByNode.size() > 1024) {
                        lastDisconnectByNode.clear();
                    }
                    lastDisconnectByNode.put(offlineNode, System.currentTimeMillis());
                }
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
            // 链路指标: 这条 SKIPPED 就是「丢单补记」, 与静默丢失区分开
            JobLinkMetrics.instance().recordMissedDispatch();
        } catch (Throwable ex) {
            log.warn("[job] 丢单记录写入失败 job={} cause={}", jobName, ex.getMessage());
        }
    }

    /**
     * 记录一条已发布命令，供执行器断线重连后补偿重放。
     *
     * @param payload      命令负载（wire 上为字符串）
     * @param targetNodeId 目标执行器节点标识，null 表示广播
     */
    private void rememberCommand(Object payload, String targetNodeId) {
        String raw = payload == null ? null : String.valueOf(payload);
        synchronized (recentCommands) {
            recentCommands.addLast(new PendingCommand(System.currentTimeMillis(), raw, targetNodeId));
            while (recentCommands.size() > RECENT_COMMAND_CAPACITY) {
                recentCommands.pollFirst();
            }
            // 链路指标: 记录缓冲水位峰值, 水位长期顶满说明执行器长时间离线
            JobLinkMetrics.instance().recordBufferDepth(recentCommands.size());
        }
    }

    /**
     * 执行器重连上线后，补偿重放其断线窗口内可能丢失的派发命令。
     *
     * <p>只重放该节点最后一次离线之后、且仍在缓冲 TTL 内的命令；首次上线
     * （无离线记录）不重放，避免把上线前的历史命令误派发给新节点。重放帧与
     * 原始帧共用同一消息 id，执行器按 id 幂等去重，重复送达不会重复执行。</p>
     *
     * @param clientId 客户端标识
     * @param nodeId   执行器节点标识
     */
    private void replayRecentCommands(String clientId, String nodeId) {
        Long lastDisconnectMs = nodeId == null ? null : lastDisconnectByNode.get(nodeId);
        if (lastDisconnectMs == null) {
            return;
        }
        long minPublishMs = Math.max(lastDisconnectMs, System.currentTimeMillis() - RECENT_COMMAND_TTL_MS);
        List<PendingCommand> snapshot;
        synchronized (recentCommands) {
            snapshot = new ArrayList<>(recentCommands);
        }
        int replayed = 0;
        for (PendingCommand pending : snapshot) {
            if (pending.publishedAtMs() < minPublishMs) {
                continue;
            }
            if (pending.targetNodeId() != null && !pending.targetNodeId().equals(nodeId)) {
                continue;
            }
            try {
                syncServer.send(clientId, JobSyncTopics.TASK_COMMAND, pending.payload());
                replayed++;
            } catch (Throwable ex) {
                log.warn("[job] cmd 重放失败 clientId={} job={} cause={}", clientId,
                        JobSyncTopics.commandJobName(pending.payload()), ex.getMessage());
            }
        }
        if (replayed > 0) {
            // 链路指标: 补偿重放帧数（与原始帧同 mid, 执行器幂等去重, 不会重复执行）
            JobLinkMetrics.instance().recordReplay(replayed);
            log.info("[job] 断线窗口命令已补偿重放 clientId={} nodeId={} count={}", clientId, nodeId, replayed);
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
