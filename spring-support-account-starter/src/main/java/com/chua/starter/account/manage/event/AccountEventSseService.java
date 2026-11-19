package com.chua.starter.account.manage.event;

import com.chua.starter.common.support.text.json.Json;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 账号页统一 SSE 事件服务。
 *
 * <p>账号页的实时推送（新邮件 {@code new-mail}、新短信 {@code new-sms}、
 * 账号变更 {@code account-changed}）统一走一条全局 SSE 连接
 * （{@code GET/POST /api/accounts/events}），替代逐功能建连。</p>
 *
 * <p><b>消息契约</b>：与 Socket 推送一致，载荷为
 * {@code {module:"account", event, data, timestamp}}，SSE {@code event:}
 * 名即 {@code event}；前端 {@code subscribe("account", event, cb)} 按
 * {@code module + event} 分发。连接保活靠约 {@value #HEARTBEAT_SECONDS}
 * 秒一次的心跳注释帧。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Slf4j
@Service
public class AccountEventSseService {

    /**
     * 心跳间隔（秒）：常见网关 60s 切空闲连接，取一半保活
     */
    private static final long HEARTBEAT_SECONDS = 25L;

    /**
     * 在线订阅（订阅标识 -> emitter）
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 订阅发号器
     */
    private final AtomicLong seq = new AtomicLong();

    /**
     * 心跳线程（守护线程，随进程退出）
     */
    private final ScheduledExecutorService heartbeat = new ScheduledThreadPoolExecutor(1, task -> { // [P3C C1 修复] 显式线程池替代 Executors 包装
        Thread thread = new Thread(task, "account-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 启动心跳。
     */
    @PostConstruct
    public void start() {
        heartbeat.scheduleAtFixedRate(this::ping, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 停止心跳并清空订阅。
     */
    @PreDestroy
    public void stop() {
        heartbeat.shutdownNow();
        emitters.clear();
    }

    /**
     * 订阅账号事件流。
     *
     * @return SSE emitter（不超时，靠心跳保活）
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        String id = "sse-" + seq.incrementAndGet();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(error -> emitters.remove(id));
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Json.toJson(payload("connected", Map.of())), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            emitters.remove(id);
            log.debug("[account-sse] 首帧发送失败: {}", e.getMessage());
        }
        log.info("[account-sse] 新订阅: id={}, online={}", id, emitters.size());
        return emitter;
    }

    /**
     * 向所有在线订阅广播事件。
     *
     * @param event 事件名（{@code new-mail} / {@code new-sms} / {@code account-changed}）
     * @param data  事件数据
     */
    public void broadcast(String event, Object data) {
        if (emitters.isEmpty()) {
            return;
        }
        String json = Json.toJson(payload(event, data));
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            SseEmitter emitter = entry.getValue();
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
                }
            } catch (Exception e) {
                emitters.remove(entry.getKey());
                log.debug("[account-sse] 推送失败，移除订阅: id={}, error={}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 心跳：发注释帧保活，失败即清理失效订阅。
     */
    private void ping() {
        if (emitters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            SseEmitter emitter = entry.getValue();
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().comment("ping"));
                }
            } catch (Exception e) {
                emitters.remove(entry.getKey());
            }
        }
    }

    /**
     * 统一载荷结构。
     *
     * @param event 事件名
     * @param data  事件数据
     * @return 载荷
     */
    private Map<String, Object> payload(String event, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>(6);
        payload.put("module", "account");
        payload.put("event", event);
        payload.put("data", data);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
}
