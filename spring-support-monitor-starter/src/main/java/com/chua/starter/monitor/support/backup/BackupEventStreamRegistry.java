package com.chua.starter.monitor.support.backup;

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
 * 数据库备份 / 恢复事件的 SSE 订阅者注册表。
 *
 * <p><b>为什么走 SSE</b>：备份中心需要实时展示备份进度（表导出进度、成功/失败）。
 * socket 推送链路（{@code MonitorPushService → MessageTemplate → SocketQueueBridge}）
 * 在队列插件未启用或 socket 组件缺失时每一跳都静默返回，前端表现为「连上了但永远
 * 收不到进度」。SSE 走 Servlet 容器自己的长连接，不依赖队列与 socket 组件，是
 * 备份进度的可靠通道。链路口径与 {@code AlarmEventStreamRegistry} 一致。</p>
 *
 * <p>广播发生在备份工作线程上，那里没有请求上下文；因此载荷必须序列化成
 * {@code byte[]} 再送，否则会被转换器链首的 {@code BinaryHttpMessageConverter}
 * 认领并写出 0 字节。</p>
 *
 * @author CH
 * @since 2026/11/24
 */
@Slf4j
@Component
public class BackupEventStreamRegistry {

    /**
     * 事件名：建连就绪。前端 {@code addEventListener("ready")}。
     */
    public static final String EVENT_READY = "ready";

    /**
     * 事件名：备份 / 恢复进度事件。前端 {@code addEventListener("progress")}。
     */
    public static final String EVENT_PROGRESS = "progress";

    /**
     * SSE 连接超时（毫秒）。0 表示交给容器，靠心跳保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 心跳间隔（毫秒）。网关对空闲长连接的普遍超时是 30~60s，取 20s 留出余量。
     */
    private static final long HEARTBEAT_MS = 20_000L;

    /**
     * 当前所有备份中心连接。
     *
     * <p>用 {@link CopyOnWriteArraySet}：广播是高频读，增删只在建连 / 断开时发生。
     * 备份中心通常同时开在少数几个标签页，规模很小。</p>
     */
    private final CopyOnWriteArraySet<SseEmitter> subscribers = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    /**
     * 构造备份事件流注册中心。
     *
     * @param objectMapper JSON 序列化器
     */
    @Autowired
    public BackupEventStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅。建连立即回 {@code ready}，让前端能马上把状态点亮成「实时」，
     * 而不是靠超时去猜连接是否成功。
     *
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.add(emitter);
        log.info("[database-backup-sse] 订阅建立，当前连接数={}", subscribers.size());

        Runnable remove = () -> remove(emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[database-backup-sse] 连接异常关闭: {}", e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[database-backup-sse] 连接超时");
            remove.run();
        });

        try {
            sendJson(emitter, EVENT_READY, Map.of("subscribers", subscribers.size()));
        } catch (IOException e) {
            log.debug("[database-backup-sse] 初始帧发送失败: {}", e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 向所有备份中心页面广播一条备份 / 恢复事件。
     *
     * <p>单条连接发送失败只摘掉该连接：备份中心可能开在多个标签页，一个标签关掉
     * 不能让其余标签停止收进度。</p>
     *
     * @param payload 事件载荷（type/phase/进度/结果等字段）
     * @return 成功送达的连接数
     */
    public int broadcast(Object payload) {
        if (subscribers.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : subscribers) {
            try {
                sendJson(emitter, EVENT_PROGRESS, payload);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("[database-backup-sse] 推送失败，摘除连接: {}", e.getMessage());
                remove(emitter);
            }
        }
        return delivered;
    }

    /**
     * 当前订阅者数量（供健康检查与诊断使用）。
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
            log.info("[database-backup-sse] 订阅关闭，剩余连接数={}", subscribers.size());
        }
    }

    /**
     * 心跳：给所有连接推注释帧保活。
     *
     * <p>没有心跳时，静默的 SSE 流会被容器或反向代理在 30~60s 后悄悄掐断，
     * 前端表现为「过一会儿就不再收到进度」且没有任何报错。</p>
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
