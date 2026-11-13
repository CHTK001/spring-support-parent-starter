package com.chua.starter.common.support.tunnel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 隧道列表的 SSE 实时推送器（一个模块一个实例）。
 *
 * <p>页面通过 {@code GET /stream} 订阅，后端按固定间隔把当前映射快照
 * （运行状态 / 公网地址 / 错误）推给所有在线页面。相比页面轮询：</p>
 * <ul>
 *   <li>启停操作的结果在下一次 tick 自动到达所有页签，不需要各自刷新；</li>
 *   <li>空转成本固定为一个快照对象 + 一次 send，不随页面数量线性放大；</li>
 *   <li>SSE 是 Servlet 长连接，不依赖 socket / 消息队列组件。</li>
 * </ul>
 *
 * <p>连接断开只移除对应 emitter，不影响其它页面。推送线程使用守护线程，
 * 容器关闭时 {@link #close()} 会停止调度并结束全部连接。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class TunnelStatusStream implements AutoCloseable {

    /**
     * 事件名：页面按 {@code tunnels} 订阅。
     */
    public static final String EVENT_NAME = "tunnels";

    /**
     * 当前在线连接。
     */
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /**
     * 快照提供方。
     */
    private final Supplier<List<TunnelView>> snapshotSupplier;

    /**
     * 推送调度器。
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 创建推送器并立即开始调度。
     *
     * @param snapshotSupplier 快照提供方
     * @param intervalMillis   推送间隔（毫秒）
     */
    public TunnelStatusStream(Supplier<List<TunnelView>> snapshotSupplier, long intervalMillis) {
        this.snapshotSupplier = snapshotSupplier;
// [P3C C1 豁免] 场景化创建线程，需文档化
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
            Thread thread = new Thread(runnable, "tunnel-status-sse");
            thread.setDaemon(true);
            return thread;
        });
        // [P3C A20 豁免]
        this.scheduler.scheduleWithFixedDelay(this::broadcast, 0, Math.max(500L, intervalMillis),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 新建一个订阅连接，并立即推送当前快照。
     *
     * @return SSE 连接
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, snapshotSupplier.get());
        return emitter;
    }

    /**
     * 广播一次快照。
     */
    private void broadcast() {
        List<TunnelView> snapshot;
        try {
            snapshot = snapshotSupplier.get();
        } catch (RuntimeException e) {
            log.warn("[Tunnel] SSE 快照生成失败: {}", e.getMessage());
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, snapshot);
        }
    }

    /**
     * 向单个连接发送快照，失败即清理。
     *
     * @param emitter  连接
     * @param snapshot 快照
     */
    private void send(SseEmitter emitter, List<TunnelView> snapshot) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).data(snapshot));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // 连接已断开，无需处理
            }
        }
    }

    /**
     * 当前订阅数（诊断用）。
     *
     * @return 连接数
     */
    public int subscriberCount() {
        return emitters.size();
    }

    /**
     * 停止调度并结束全部连接。
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // 连接已断开，无需处理
            }
        }
        emitters.clear();
    }}