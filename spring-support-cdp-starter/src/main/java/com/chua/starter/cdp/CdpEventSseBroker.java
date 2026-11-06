// [P3C C1 豁免] 场景化创建单线程心跳调度器，需文档化
package com.chua.starter.cdp;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.cdp.service.CdpEngineService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * CDP 全局事件 SSE 广播器（调试中心唯一的一条事件流）
 *
 * <p>把 {@link CdpEngineService#subscribeAll(Consumer)} 收到的 CDP 事件
 * （Console / Network / Page / Target）统一转成一条 {@code text/event-stream} 推给前端。
 * 前端 {@code pages/debug} 只连这一个 SSE，不再为每个 WS 会话重复订阅，也不再定时轮询后端。</p>
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li><b>单一订阅者</b>：全局只向引擎注册一个 handler（{@link #subscribed} 保证），
 *       由它广播给所有活跃 emitter；连接断开最后一个 emitter 时 {@link #unsubscribeAll} 注销，
 *       彻底消除「每刷新一次页面就往 {@code globalSubscribers} 追加一个永不移除的 handler」的泄漏。</li>
 *   <li><b>事件帧沿用 WS 的 Push 格式</b>：{@code {"event":"Push","method","params","targetId"}}，
 *       前端可复用同一套 {@code appendConsole / appendNetwork / onPageChanged} 分发逻辑。</li>
 *   <li><b>心跳与 emitter 生命周期绑定</b>：每个连接一个 15s 心跳，断线即取消，防止代理 idle 超时，
 *       也避免死任务堆积。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdpEventSseBroker {

    /** SSE 心跳间隔（秒） */
    private static final long HEARTBEAT_SECONDS = 15L;

    private final CdpEngineService engine;

    /** 当前所有活跃的 SSE 连接 */
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /** 是否已向引擎注册全局事件订阅者（懒注册，最后一个连接断开时复位） */
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    /** 心跳调度器（守护线程，随应用退出） */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "cdp-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /** 全局事件订阅者：把一条 CDP 事件广播给所有 emitter */
    private final Consumer<Map<String, Object>> broadcaster = this::broadcast;

    /**
     * 新建一个 SSE 连接并接入全局事件流
     *
     * @return emitter（{@code new SseEmitter(0L)} 表示不超时，靠心跳保活）
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        ensureSubscribed();

        // 心跳 future 与 emitter 生命周期绑定：断线即取消，杜绝死任务堆积
        ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                cancelQuietly(holder[0]);
                emitter.complete();
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        holder[0] = heartbeat;

        Runnable cleanup = () -> {
            cancelQuietly(holder[0]);
            emitters.remove(emitter);
            // 没有任何前端在听时，把全局订阅者摘掉，避免向空集合白转发
            if (emitters.isEmpty() && subscribed.compareAndSet(true, false)) {
                engine.unsubscribeAll(broadcaster);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());

        // 先给一条 connected 帧，让前端能区分「SSE 已连上」与「仅 HTTP 200」
        try {
            emitter.send(SseEmitter.event().name("connected").data("1"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** 懒注册：只有第一个 SSE 连接进来才向引擎登记全局订阅者 */
    private void ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            engine.subscribeAll(broadcaster);
            log.info("[cdp] 全局 SSE 事件流已建立");
        }
    }

    /** 把一条 CDP 事件组装成 Push 帧，逐个 emitter 发送 */
    private void broadcast(Map<String, Object> msg) {
        if (emitters.isEmpty()) {
            return;
        }
        // 用 LinkedHashMap 逐字段放入：Map.of 不接受 null 值，
        // 一旦某个事件没有 targetId 就会抛 NPE，整条推送被静默丢掉。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "Push");
        payload.put("method", msg.get("method"));
        payload.put("params", msg.get("params"));
        payload.put("targetId", msg.get("targetId"));
        String data;
        try {
            data = Json.toJson(payload);
        } catch (Exception e) {
            log.warn("[cdp] SSE 事件序列化失败: {}", e.getMessage());
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("cdp").data(data));
            } catch (Exception e) {
                // 单个连接写失败：移除，交给其 onError/onCompletion 收尾
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 已断开，忽略
                }
            }
        }
    }

    /** 幂等取消心跳任务 */
    private static void cancelQuietly(ScheduledFuture<?> future) {
        if (future == null || future.isCancelled()) {
            return;
        }
        try {
            future.cancel(true);
        } catch (Exception ignored) {
            // 忽略取消异常
        }
    }

    /** 应用退出：注销订阅 + 关闭心跳线程 */
    @PreDestroy
    public void destroy() {
        if (subscribed.compareAndSet(true, false)) {
            engine.unsubscribeAll(broadcaster);
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 忽略
            }
        }
        emitters.clear();
        scheduler.shutdownNow();
    }
}
