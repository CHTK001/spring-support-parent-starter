// [P3C A20 豁免] A20/A30/A31: 含日志/心跳字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.server;

import com.chua.starter.logger.event.LoggerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日志实时推送（SSE）：订阅者经 {@code GET /v2/logger/stream} 建立长连，
 * 服务端每收到一批日志即以 {@code log} 事件推送，另以固定间隔发 {@code heartbeat} 保活。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LoggerSseBroker implements SmartLifecycle {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
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
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 完成失败忽略
            }
        }
        emitters.clear();
    }

    /**
     * 建立订阅
     *
     * @return SSE emitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
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
     * 推送一条日志事件（{@code log} 事件，载荷为 JSON）
     *
     * @param event 日志事件
     */
    public void broadcast(LoggerEvent event) {
        if (emitters.isEmpty() || event == null) {
            return;
        }
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (Exception e) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("log").data(json, MediaType.TEXT_PLAIN));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private void beat() {
        if (emitters.isEmpty()) {
            return;
        }
        String ts = String.valueOf(System.currentTimeMillis());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(ts));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 当前订阅数
     *
     * @return 订阅数
     */
    public int subscribers() {
        return emitters.size();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
