// [P3C A20 豁免] A20/A30/A31: 含日志/心跳字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.server;

import com.chua.starter.logger.event.LoggerEvent;
import com.chua.starter.logger.store.LoggerEventStore;
import com.chua.starter.logger.store.MemoryLoggerEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志实时推送（SSE）：订阅者经 {@code GET /v2/logger/stream} 建立长连，
 * 服务端每收到一批日志即以 {@code log} 事件推送，另以固定间隔发 {@code heartbeat} 保活。
 *
 * <p>订阅时可带 app/instance/level/keyword/traceId 过滤条件（与 {@code /events} 同口径），
 * 服务端按订阅过滤后再推送，避免前端收到并再丢弃不相关事件。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LoggerSseBroker implements SmartLifecycle {

    /** 高频写入最多每五秒合并计算一次；无写入时每三十秒推进统计时间窗口。 */
    private static final long STATS_MERGE_MS = 5_000L;
    private static final long STATS_WINDOW_MS = 30_000L;
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Map<StatsFilter, StatsGroup> statsGroups = new ConcurrentHashMap<>();
    private final LoggerEventStore store;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long heartbeatSeconds;
    private ScheduledExecutorService heartbeat;
    private ScheduledExecutorService statsWorker;
    private volatile boolean running;

    /**
     * @param heartbeatSeconds 心跳间隔（秒）
     */
    public LoggerSseBroker(long heartbeatSeconds) {
        this(heartbeatSeconds, null);
    }

    /**
     * @param heartbeatSeconds 心跳间隔（秒）
     * @param store 统计快照的真实存储；旧构造器仅提供日志流
     */
    public LoggerSseBroker(long heartbeatSeconds, LoggerEventStore store) {
        this.heartbeatSeconds = heartbeatSeconds <= 0 ? 15L : heartbeatSeconds;
        this.store = store;
    }

    /**
     * 启动心跳
     */
    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        heartbeat = Executors.newSingleThreadScheduledExecutor(task ->
                Thread.ofPlatform().name("logger-sse-heartbeat").daemon(true).unstarted(task));
        heartbeat.scheduleAtFixedRate(this::beat, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
        if (store != null) {
            statsWorker = Executors.newSingleThreadScheduledExecutor(task ->
                    Thread.ofPlatform().name("logger-sse-stats").daemon(true).unstarted(task));
            statsWorker.scheduleWithFixedDelay(() -> refreshStats(System.currentTimeMillis()),
                    0L, 1L, TimeUnit.SECONDS);
        }
        log.info("[logger] SSE 推送通道已启动, heartbeat={}s", heartbeatSeconds);
    }

    /**
     * 停止并清空订阅
     */
    @Override
    public void stop() {
        running = false;
        if (heartbeat != null) {
            heartbeat.shutdownNow();
            heartbeat = null;
        }
        if (statsWorker != null) {
            statsWorker.shutdownNow();
            statsWorker = null;
        }
        for (StatsGroup group : statsGroups.values()) {
            for (StatsClient client : group.clients) {
                client.emitter.complete();
            }
        }
        statsGroups.clear();
        for (Subscription subscription : subscriptions) {
            try {
                subscription.emitter().complete();
            } catch (Exception ignored) {
                // 完成失败忽略
            }
        }
        subscriptions.clear();
    }

    /**
     * 建立订阅（可带过滤条件，空条件 = 全量）
     *
     * @param app      应用过滤
     * @param instance 实例过滤
     * @param level    级别过滤（逗号分隔多值）
     * @param keyword  关键字过滤（对 message/logger 匹配）
     * @param traceId  链路追踪标识过滤
     * @return SSE emitter
     */
    public SseEmitter subscribe(String app, String instance, String level, String keyword, String traceId) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter, app, instance,
                MemoryLoggerEventStore.levelSet(level),
                keyword == null ? "" : keyword.trim().toLowerCase(),
                traceId);
        subscriptions.add(subscription);
        Runnable remove = () -> subscriptions.remove(subscription);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(cause -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("open").data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    /**
     * 推送一条日志事件（{@code log} 事件，载荷为 JSON）；仅发给命中订阅条件的订阅者
     *
     * @param event 日志事件
     */
    public void broadcast(LoggerEvent event) {
        if (event != null) {
            invalidateStats(event.getApp(), event.getInstance());
        }
        if (subscriptions.isEmpty() || event == null) {
            return;
        }
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (Exception e) {
            return;
        }
        for (Subscription subscription : subscriptions) {
            if (!subscription.matches(event)) {
                continue;
            }
            try {
                subscription.emitter().send(SseEmitter.event().name("log").data(json, MediaType.TEXT_PLAIN));
            } catch (Exception e) {
                subscriptions.remove(subscription);
            }
        }
    }

    private void beat() {
        String ts = String.valueOf(System.currentTimeMillis());
        for (Subscription subscription : subscriptions) {
            try {
                subscription.emitter().send(SseEmitter.event().name("heartbeat").data(ts));
            } catch (Exception e) {
                subscriptions.remove(subscription);
            }
        }
        for (StatsGroup group : statsGroups.values()) {
            for (StatsClient client : group.clients) {
                sendStats(group, client, "heartbeat", ts);
            }
        }
    }

    /**
     * 订阅完整统计快照。相同 app/instance 共用一份快照；HTTP 线程不扫描存储。
     *
     * @param app 应用过滤，空为全局
     * @param instance 实例过滤，空为全部实例
     * @return 统计 SSE emitter（stats / stats-error / heartbeat）
     */
    public SseEmitter subscribeStats(String app, String instance) {
        if (store == null) {
            throw new IllegalStateException("统计流未配置日志存储");
        }
        StatsFilter filter = new StatsFilter(app, instance);
        SseEmitter emitter = new SseEmitter(0L);
        StatsClient client = new StatsClient(emitter);
        StatsGroup group = statsGroups.compute(filter, (key, current) -> {
            StatsGroup target = current == null ? new StatsGroup(key) : current;
            target.clients.add(client);
            return target;
        });
        Runnable remove = () -> removeStatsClient(group, client);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(cause -> remove.run());
        sendStats(group, client, "open", "ok");
        return emitter;
    }

    /**
     * 只标记受影响的统计组，不在日志接收线程计算。删除事件后也需调用此方法。
     *
     * @param app 变更应用，空表示可能影响所有应用
     * @param instance 变更实例，空表示可能影响所有实例
     */
    public void invalidateStats(String app, String instance) {
        for (StatsGroup group : statsGroups.values()) {
            if ((app == null || app.isBlank() || group.filter.app().isEmpty() || group.filter.app().equals(app))
                    && (instance == null || instance.isBlank() || group.filter.instance().isEmpty()
                    || group.filter.instance().equals(instance))) {
                group.version.incrementAndGet();
            }
        }
    }

    /** 单个后台线程串行执行；包级入口便于不启动服务的确定性校验。 */
    void refreshStats(long now) {
        for (StatsGroup group : statsGroups.values()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            long version = group.version.get();
            boolean due = group.snapshot == null || version != group.computedVersion
                    || now - group.computedAt >= STATS_WINDOW_MS;
            if (due && (group.attemptedAt == 0L || now - group.attemptedAt >= STATS_MERGE_MS)) {
                group.attemptedAt = now;
                try {
                    Map<String, Object> snapshot = new LinkedHashMap<>(
                            store.stats(group.filter.app(), group.filter.instance()));
                    snapshot.put("snapshotAt", System.currentTimeMillis());
                    group.snapshot = mapper.writeValueAsString(snapshot);
                    group.computedVersion = version;
                    group.computedAt = now;
                    group.snapshotVersion++;
                } catch (Exception e) {
                    // 不发日志风暴；失败不标记为最新，下一轮按合并间隔重试。
                    for (StatsClient client : group.clients) {
                        sendStats(group, client, "stats-error", "统计快照暂不可用");
                    }
                    continue;
                }
            }
            if (group.snapshot == null) {
                continue;
            }
            for (StatsClient client : group.clients) {
                if (client.snapshotVersion != group.snapshotVersion
                        && sendStats(group, client, "stats", group.snapshot)) {
                    client.snapshotVersion = group.snapshotVersion;
                }
            }
        }
    }

    private boolean sendStats(StatsGroup group, StatsClient client, String name, String data) {
        try {
            client.emitter.send(SseEmitter.event().name(name).data(data, MediaType.TEXT_PLAIN));
            return true;
        } catch (Exception e) {
            removeStatsClient(group, client);
            // IOException 由 Servlet 容器完成错误分派，不能再 completeWithError。
            return false;
        }
    }

    private void removeStatsClient(StatsGroup group, StatsClient client) {
        statsGroups.computeIfPresent(group.filter, (key, current) -> {
            if (current != group) {
                return current;
            }
            current.clients.remove(client);
            return current.clients.isEmpty() ? null : current;
        });
    }

    /** 标准化后的统计共享键。 */
    private record StatsFilter(String app, String instance) {
        private StatsFilter {
            app = app == null ? "" : app.trim();
            instance = instance == null ? "" : instance.trim();
        }
    }

    /** 单一工作线程拥有快照字段，事件线程只递增版本号。 */
    private static final class StatsGroup {
        private final StatsFilter filter;
        private final CopyOnWriteArrayList<StatsClient> clients = new CopyOnWriteArrayList<>();
        private final AtomicLong version = new AtomicLong();
        private long computedVersion = -1L;
        private long attemptedAt;
        private long computedAt;
        private long snapshotVersion;
        private String snapshot;

        private StatsGroup(StatsFilter filter) {
            this.filter = filter;
        }
    }

    /** 每个连接仅记录已收到的快照版本。 */
    private static final class StatsClient {
        private final SseEmitter emitter;
        private long snapshotVersion = -1L;

        private StatsClient(SseEmitter emitter) {
            this.emitter = emitter;
        }
    }

    /**
     * 当前订阅数
     *
     * @return 订阅数
     */
    public int subscribers() {
        return subscriptions.size();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 一条 SSE 订阅：emitter + 该订阅声明的过滤条件
     */
    private record Subscription(SseEmitter emitter, String app, String instance,
                                Set<String> levels, String keyword, String traceId) {

        boolean matches(LoggerEvent event) {
            return MemoryLoggerEventStore.match(event, app, instance, levels, keyword, traceId, null, null);
        }
    }
}
