// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 会话事件控制器（SSE）
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/events")
@RequiredArgsConstructor
public class AiSessionEventController {

// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 建立 SSE 连接
     *
     * @return SseEmitter
     */
    @GetMapping(value = "/sessions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sessions() {
        SseEmitter emitter = new SseEmitter(0L); // 不超时

        // 心跳 future 必须与 emitter 生命周期绑定。
        // 客户端断线后会重连，若 future 不取消，每次重连都会永久多出一个每 30s 触发的死任务，
        // 已失效的 emitter 被 ScheduledFuture 一直引用；单线程调度器被逐步占满，
        // 任务数与被持有的连接数无上限增长，最终拖垮进程。
        ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];

        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (Exception e) {
                // 客户端已断开：取消心跳并结束流，避免任务泄漏
                cancelQuietly(holder[0]);
                emitter.complete();
            }
        }, 0, 30, TimeUnit.SECONDS);
        holder[0] = heartbeat;

        emitter.onCompletion(() -> cancelQuietly(holder[0]));
        emitter.onError(t -> cancelQuietly(holder[0]));
        emitter.onTimeout(() -> cancelQuietly(holder[0]));

        return emitter;
    }

    /**
     * 取消心跳任务（幂等，忽略取消异常）。
     *
     * @param future 心跳任务
     */
    private void cancelQuietly(ScheduledFuture<?> future) {
        if (future == null || future.isCancelled()) {
            return;
        }
        try {
            future.cancel(false);
        } catch (Exception ignored) {
            // 忽略：取消失败不影响连接结束
        }
    }}