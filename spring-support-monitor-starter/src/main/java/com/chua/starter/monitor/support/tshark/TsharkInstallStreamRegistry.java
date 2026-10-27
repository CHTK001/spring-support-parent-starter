package com.chua.starter.monitor.support.tshark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * tshark 安装进度的 SSE 订阅者注册表。
 *
 * <p>与 {@link TsharkPacketStreamRegistry} 同构，只少了「按会话分组」这一层：
 * 安装是全局唯一的一件事，所有打开的页面看到的进度都一样。</p>
 *
 * <p><b>为什么不做成轮询</b>：安装包含下载（几十到上百 MB），轮询要么太密打满连接、
 * 要么太疏看不到进度；而且失败时页面应当在同一次交互里立刻看到原因。SSE 一条长连接
 * 就能把「下载 42% → 解压 → 完成 / 失败」按发生顺序推过去。</p>
 *
 * <p>连上先回一帧当前状态：页面可能在安装中途才打开，不该从 0% 开始猜。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class TsharkInstallStreamRegistry {

    /**
     * 事件名：进度（下载 / 解压 / 完成 / 失败都走这一个事件，载荷自带 state）。
     */
    public static final String EVENT_PROGRESS = "progress";

    /**
     * 事件名：建连就绪。
     */
    public static final String EVENT_READY = "ready";

    /**
     * SSE 连接超时（毫秒）。0 表示不超时，由心跳保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 订阅者集合。广播是高频读、增删只在建连 / 断开发生，用写时复制集合。
     */
    private final Set<SseEmitter> subscribers = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    /**
     * 构造器。
     *
     * @param objectMapper JSON 序列化器（SSE 载荷必须序列化成 byte[]，原因见
     *                     {@link TsharkPacketStreamRegistry#sendJson}）
     */
    @Autowired
    public TsharkInstallStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅。
     *
     * @param snapshot 建连时先推给前端的当前状态，可为 null
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe(TsharkRuntimeStatus snapshot) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.add(emitter);
        log.info("[tshark] 安装进度订阅建立，当前连接数={}", subscribers.size());

        Runnable remove = () -> remove(emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[tshark] 安装进度连接异常关闭 reason={}", e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[tshark] 安装进度连接超时");
            remove.run();
        });

        try {
            sendJson(emitter, EVENT_READY, snapshot == null
                    ? TsharkRuntimeStatus.of(TsharkInstallState.CHECKING, "正在探测 tshark")
                    : snapshot);
        } catch (IOException e) {
            log.debug("[tshark] 安装进度初始帧发送失败 reason={}", e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 广播一帧状态。
     *
     * <p>单连接失败只摘掉该连接：一个标签页关掉不能让别的页面停止更新。</p>
     *
     * @param status 状态快照
     * @return 成功送达的连接数
     */
    public int broadcast(TsharkRuntimeStatus status) {
        if (subscribers.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : subscribers) {
            try {
                sendJson(emitter, EVENT_PROGRESS, status);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("[tshark] 安装进度推送失败，摘除连接 reason={}", e.getMessage());
                remove(emitter);
            }
        }
        return delivered;
    }

    /**
     * 序列化后发送一帧。
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷
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
            log.info("[tshark] 安装进度订阅关闭，剩余连接数={}", subscribers.size());
        }
    }

    /**
     * 心跳保活：空闲长连接会被网关悄悄掐断，前端表现为「进度停在 42% 不动了」。
     */
    @Scheduled(fixedRate = 20_000L, initialDelay = 20_000L)
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
