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

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
    * 建立 SSE 连接
    *
    * @return SseEmitter
    */
    @GetMapping(value = "/sessions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sessions() {
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时

        // 发送心跳
        scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (IOException e) {
                emitter.complete();
            }
        }, 0, 30, TimeUnit.SECONDS);

        return emitter;
    }
}
