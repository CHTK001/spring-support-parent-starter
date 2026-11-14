package com.chua.starter.monitor.support.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 服务管理 —— 全局状态变更 SSE 订阅者注册表。
 *
 * <p><b>为什么单独建一条 SSE</b>：服务节点状态是「运行态」而不是库列，
 * {@link ServerNodeRegistry} 里 {@code Server.isRunning()} 没有生命周期回调，
 * 进程被杀 / 端口被占导致的 ERROR 没有任何事件源。原先靠前端每 10 秒短轮询
 * {@code /summary}+{@code /page} 兜底；这里改为 Servlet 容器直连的长连接，
 * 由 {@link ServerEventStreamService} 在<b>状态指纹变化时才推一帧</b>，
 * 前端收到后静默重取 —— 无变化时不产生任何 HTTP 请求，一条连接服务整个页面，
 * 多个标签页由本注册表统一广播。</p>
 *
 * <p>与告警 SSE（{@code AlarmEventStreamRegistry}）同构：不依赖消息队列与 socket
 * 组件，避免「连上了却收不到推送」的静默失败。广播发生在调度线程上（无请求上下文），
 * 因此载荷必须序列化成 {@code byte[]} 再送，否则会被转换器链首的
 * {@code BinaryHttpMessageConverter} 认领并写出 0 字节。</p>
 *
 * <p>连接端点见 {@code ServerManageController#stream()}：用 <b>POST</b> 而非 GET，
 * 与全局 {@code /sse/connect} 的 POST SSE 约定一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
public class ServerEventStreamRegistry {

    /**
     * 事件名：建连就绪。仅表示通道已通，前端不据此重取数据（首屏由页面自己拉）。
     */
    public static final String EVENT_READY = "ready";

    /**
     * 事件名：节点定义 / 运行态已变化。前端收到后静默重取摘要与当前页。
     */
    public static final String EVENT_CHANGED = "changed";

    /**
     * SSE 连接超时（毫秒）。0 表示交给容器，靠心跳保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 心跳间隔（毫秒）。网关对空闲长连接的普遍超时是 30~60s，取 20s 留出余量。
     */
    private static final long HEARTBEAT_MS = 20_000L;

    /**
     * 当前所有服务管理页连接。用 {@link CopyOnWriteArraySet}：广播是高频读，
     * 增删只在建连 / 断开时发生，页面通常只开在少数几个标签页。
     */
    private final CopyOnWriteArraySet<SseEmitter> subscribers = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    public ServerEventStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅。建连立即回 {@code ready}，让前端能马上确认连接就绪。
     *
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.add(emitter);
        log.info("[server-sse] 订阅建立，当前连接数={}", subscribers.size());

        Runnable remove = () -> remove(emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[server-sse] 连接异常关闭: {}", e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[server-sse] 连接超时");
            remove.run();
        });

        try {
            sendJson(emitter, EVENT_READY, Map.of("ready", true));
        } catch (IOException e) {
            log.debug("[server-sse] 初始帧发送失败: {}", e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 向所有服务管理页广播一条「状态已变化」事件。
     *
     * <p>单条连接发送失败只摘掉该连接：一个标签页关闭不能让其余标签停止收推送。</p>
     *
     * @param payload 变更载荷（目前只带 reason，前端收到后自行重取，不信任推送内容）
     * @return 成功送达的连接数
     */
    public int broadcast(Object payload) {
        if (subscribers.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : subscribers) {
            try {
                sendJson(emitter, EVENT_CHANGED, payload);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("[server-sse] 推送失败，摘除连接: {}", e.getMessage());
                remove(emitter);
            }
        }
        return delivered;
    }

    /**
     * 当前订阅者数量（供状态探测判断是否需要计算指纹）。
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
            log.info("[server-sse] 订阅关闭，剩余连接数={}", subscribers.size());
        }
    }

    /**
     * 心跳：给所有连接推注释帧保活。注释帧无 data，前端 SSE 客户端会丢弃，
     * 不会触发任何重取。
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
