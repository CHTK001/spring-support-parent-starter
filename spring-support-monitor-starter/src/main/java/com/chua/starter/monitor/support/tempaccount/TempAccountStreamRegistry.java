package com.chua.starter.monitor.support.tempaccount;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 临时账号消息的 SSE 订阅者注册表。
 *
 * <p>与告警 SSE（{@code AlarmEventStreamRegistry}）同一模式：Servlet 容器长连接，
 * 不依赖队列与 socket 组件。临时账号抽屉打开时建立一条订阅，后端轮询到
 * 号码新短信 / 邮箱新邮件后，向所有订阅广播增量事件。</p>
 *
 * <p><b>载荷序列化</b>：广播发生在轮询工作线程上（无请求上下文），载荷必须
 * 序列化成 {@code byte[]} 再送，否则会被转换器链首的
 * {@code BinaryHttpMessageConverter} 认领并写出 0 字节。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class TempAccountStreamRegistry {

    /**
     * 事件名：建连就绪。前端 {@code addEventListener("ready")}。
     */
    public static final String EVENT_READY = "ready";

    /**
     * 事件名：账号新增。前端 {@code addEventListener("account")}。
     */
    public static final String EVENT_ACCOUNT = "account";

    /**
     * 事件名：新消息（短信 / 邮件）。前端 {@code addEventListener("message")}。
     */
    public static final String EVENT_MESSAGE = "message";

    /**
     * 事件名：账号删除。前端 {@code addEventListener("remove")}。
     */
    public static final String EVENT_REMOVE = "remove";

    /**
     * SSE 连接超时（毫秒）。0 表示交给容器，靠心跳保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 心跳间隔（毫秒）。网关对空闲长连接的普遍超时是 30~60s，取 20s 留出余量。
     */
    private static final long HEARTBEAT_MS = 20_000L;

    /**
     * 当前所有临时账号页连接。
     */
    private final CopyOnWriteArraySet<SseEmitter> subscribers = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    @Autowired
    public TempAccountStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅。建连立即回 {@code ready}，让前端能马上把状态点亮成「实时」。
     *
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.add(emitter);
        log.info("[temp-account-sse] 订阅建立，当前连接数={}", subscribers.size());

        Runnable remove = () -> remove(emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[temp-account-sse] 连接异常关闭: {}", e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[temp-account-sse] 连接超时");
            remove.run();
        });

        try {
            sendJson(emitter, EVENT_READY, Map.of("subscribers", subscribers.size()));
        } catch (IOException e) {
            log.debug("[temp-account-sse] 初始帧发送失败: {}", e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 向所有订阅广播一条事件。
     *
     * @param event   事件名
     * @param payload 载荷
     * @return 成功送达的连接数
     */
    public int broadcast(String event, Object payload) {
        if (subscribers.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : subscribers) {
            try {
                sendJson(emitter, event, payload);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("[temp-account-sse] 推送失败，摘除连接: {}", e.getMessage());
                remove(emitter);
            }
        }
        return delivered;
    }

    /**
     * 当前订阅者数量。
     *
     * @return 连接数
     */
    public int subscriberCount() {
        return subscribers.size();
    }

    /**
     * 把载荷序列化成 JSON 字节后作为事件帧发出。
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷对象
     * @throws IOException 序列化失败或连接已断
     */
    private void sendJson(SseEmitter emitter, String event, Object payload) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(payload);
        emitter.send(SseEmitter.event().name(event).data(json));
    }

    /**
     * 移除一条连接。
     *
     * @param emitter 目标连接
     */
    private void remove(SseEmitter emitter) {
        if (subscribers.remove(emitter)) {
            log.info("[temp-account-sse] 订阅关闭，剩余连接数={}", subscribers.size());
        }
    }

    /**
     * 心跳：给所有连接推注释帧保活。
     */
    @Scheduled(fixedRate = HEARTBEAT_MS, initialDelay = HEARTBEAT_MS)
    public void heartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                remove(emitter);
            }
        }
    }
}