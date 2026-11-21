// [P3C A20 豁免] A20/A30/A31: 含日志/心跳字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.server;

import com.chua.starter.logger.event.LoggerEvent;
import com.chua.starter.logger.store.MemoryLoggerEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final long heartbeatSeconds;
    private ScheduledExecutorService heartbeat;
    private volatile boolean running;

    /**
     * @param heartbeatSeconds 心跳间隔（秒）
     */
    public LoggerSseBroker(long heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds <= 0 ? 15L : heartbeatSeconds;
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
        if (subscriptions.isEmpty()) {
            return;
        }
        String ts = String.valueOf(System.currentTimeMillis());
        for (Subscription subscription : subscriptions) {
            try {
                subscription.emitter().send(SseEmitter.event().name("heartbeat").data(ts));
            } catch (Exception e) {
                subscriptions.remove(subscription);
            }
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
