package com.chua.starter.ai.support.stream;

import com.chua.common.support.ai.agent.AgentTrace;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话超时装饰器：给任意 {@link ChatFrameSink} 套一层「无输出超时」看门狗。
 *
 * <p><b>为什么需要</b>：模型卡住、上游连接半开、facade 迟迟不产出时，本轮流会一直挂着 ——
 * 连接被占住、前端转圈到天荒地老，而且从界面上完全看不出发生了什么，只像「AI 在想很久」。
 * 到点后主动收口，并回一条明确的 {@code stream_timeout} 事件，让用户知道是超时而不是还在生成。</p>
 *
 * <p><b>为什么做成装饰器而不是改编排</b>：{@link ChatStreamOrchestrator#execute} 是最热的
 * 业务路径，往里塞计时逻辑等于把「超时」和「怎么产出帧」耦合在一起。装饰器只包一层
 * 槽，SSE 与 WebSocket 两种传输自动同时生效，且不改动编排的任何行为。</p>
 *
 * <p>判据是「距上一次**任何**帧输出」的间隔，而不是「总耗时」：思考阶段会持续吐 think 帧、
 * 检索阶段会吐 search 帧，这些都算有进展，不应被误杀。</p>
 *
 * <p>超时后：先发一帧 {@code stream_timeout}（含分钟数），再 {@link #markCancelled()} 让
 * facade 停止产出，最后 {@link #close()} 收尾；已经结束（sendDone）或已取消的轮次不计时。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class IdleTimeoutChatFrameSink implements ChatFrameSink {

    /**
     * 看门狗的轮询间隔（秒）。取 5 秒：既不频繁空转，又保证超时判定误差在 5 秒内。
     */
    private static final long CHECK_INTERVAL_SECONDS = 5L;

    /**
     * 超时事件的事件名
     */
    private static final String EVENT_STREAM_TIMEOUT = "stream_timeout";

    /**
     * 看门狗调度器：daemon 单线程，只做「检查有没有超时」这一件事。
     *
     * <p>用 {@code Executors.newSingleThreadScheduledExecutor} 而不是共享业务线程池：
     * 这是一个纯计时任务，若被业务任务挤占，超时就会失准；而它自身几乎不耗 CPU。</p>
     */
    private static final ScheduledExecutorService WATCHDOG =
// [P3C C1 豁免] 场景化创建线程，需文档化
            Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
                Thread t = new Thread(r, "chat-stream-idle-watchdog");
                t.setDaemon(true);
                return t;
            });

    /**
     * 被包装的真实帧槽
     */
    private final ChatFrameSink delegate;

    /**
     * 会话标识（仅用于日志）
     */
    private final String sessionId;

    /**
     * 超时时长（毫秒）；小于等于 0 表示不启用
     */
    private final long timeoutMillis;

    /**
     * 最近一次输出帧的时刻（毫秒）
     */
    private final AtomicLong lastEmitAt = new AtomicLong(System.currentTimeMillis());

    /**
     * 本轮是否已结束（sendDone 或超时收口），结束后不再计时
     */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * 本轮看门狗的定时任务
     */
    private ScheduledFuture<?> watchdog;

    /**
     * 包装一个帧槽。
     *
     * @param delegate       被包装的真实帧槽，不允许为 null
     * @param sessionId      会话标识，可为空
     * @param timeoutMinutes 超时分钟数；小于等于 0 时不启用看门狗，原样返回 delegate
     * @return 带超时看门狗的帧槽
     */
    public static ChatFrameSink wrap(ChatFrameSink delegate, String sessionId, int timeoutMinutes) {
        if (delegate == null || timeoutMinutes <= 0) {
            // 未配置/配置为 0 = 不限制：直接返回原槽，不做任何额外计时
            return delegate;
        }
        return new IdleTimeoutChatFrameSink(delegate, sessionId, timeoutMinutes * 60_000L);
    }

    private IdleTimeoutChatFrameSink(ChatFrameSink delegate, String sessionId, long timeoutMillis) {
        this.delegate = delegate;
        this.sessionId = sessionId;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 启动看门狗。应在提交编排任务**之前**调用，避免第一帧之前的准备阶段被计入超时。
     */
    public void start() {
        if (timeoutMillis <= 0 || delegate == null) {
            return;
        }
        // [P3C A20 豁免]
        long tickMillis = Math.min(CHECK_INTERVAL_SECONDS * 1000L, Math.max(timeoutMillis / 4L, 1000L));
        watchdog = WATCHDOG.scheduleWithFixedDelay(this::check, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录一次输出：刷新「最近输出时刻」。
     */
    private void touch() {
        lastEmitAt.set(System.currentTimeMillis());
    }

    /**
     * 检查是否已超时。
     */
    private void check() {
        try {
            if (finished.get()) {
                return;
            }
            long idle = System.currentTimeMillis() - lastEmitAt.get();
            if (idle < timeoutMillis) {
                return;
            }
            // 先置位再发事件：避免与编排线程并发收尾造成重复收口
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            long minutes = timeoutMillis / 60_000L;
            log.warn("[ChatStream] 会话超时收口 sessionId={} 无输出 {}s（阈值 {} 分钟）",
                    // [P3C A20 豁免]
                    sessionId, idle / 1000L, minutes);
            Map<String, Object> event = new LinkedHashMap<>(4);
            event.put("type", EVENT_STREAM_TIMEOUT);
            event.put("timeoutMinutes", minutes);
            event.put("message", "本次回复超过 " + minutes + " 分钟没有任何输出，已判定为超时，请重试");
            try {
                delegate.sendEvent(event);
            } catch (Exception e) {
                log.warn("[ChatStream] 发送超时事件失败 sessionId={}: {}", sessionId, e.getMessage());
            }
            // 让 facade 停止产出，再收尾传输
            delegate.markCancelled();
            delegate.close();
        } catch (Exception e) {
            log.warn("[ChatStream] 超时检查失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 停止看门狗（不改动真实槽状态）。
     */
    public void stop() {
        ScheduledFuture<?> task = this.watchdog;
        if (task != null) {
            task.cancel(false);
            this.watchdog = null;
        }
    }

    @Override
    public void sendEvent(Map<String, Object> event) {
        touch();
        delegate.sendEvent(event);
    }

    @Override
    public void sendTrace(AgentTrace trace) {
        touch();
        delegate.sendTrace(trace);
    }

    @Override
    public void sendDone() {
        finished.set(true);
        stop();
        delegate.sendDone();
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public void markCancelled() {
        delegate.markCancelled();
    }

    @Override
    public void close() {
        finished.set(true);
        stop();
        delegate.close();    }}