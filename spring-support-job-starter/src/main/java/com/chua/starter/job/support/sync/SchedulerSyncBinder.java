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

import java.util.List;
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
 // 0 在线 执行器: 退化为 broadcast (历史行为, 等待下次 tick)
                    syncServer.publish(JobSyncTopics.TASK_COMMAND, payload);
                    log.info("[job] cmd 已发 (broadcast fallback) topic={} payload={}",
                            JobSyncTopics.TASK_COMMAND, payload);
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
            /** on消息 */
            public void onMessage(String clientId, String topic, Object message) {
                if (JobSyncTopics.TASK_LOG.equals(topic)) {
                    log.info("[job] SCHEDULER 收到上行 TASK_LOG clientId={} messageType={}",
                            clientId, message == null ? "null" : message.getClass().getSimpleName());
                    logHandler.handle(topic, message);
                } else if (JobSyncTopics.REGISTER.equals(topic)) {
                    log.info("[job] executor 上线 clientId={} payload={}", clientId, message);
                }
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
