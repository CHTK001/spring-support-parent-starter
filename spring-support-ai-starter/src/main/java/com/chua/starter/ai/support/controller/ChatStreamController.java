package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.stream.ChatStreamOrchestrator;
import com.chua.starter.ai.support.stream.ChatStreamRegistry;
import com.chua.starter.ai.support.stream.PreparedChat;
import com.chua.starter.ai.support.stream.SseChatFrameSink;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.OutputStream;
import java.util.Map;

/**
 * Guada 前端聊天 SSE 控制器（薄传输适配层）。
 *
 * <p>聊天的解析、归属校验、落库与流式编排全部在 {@link ChatStreamOrchestrator}（SSE/WebSocket 共用）；
 * 本控制器只负责建立 SSE 响应、把帧写成裸 {@code data:} 字节。停止逻辑见 {@link ChatStreamRegistry}。</p>
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/chat")
@ApiReturnFormatIgnore
@RequiredArgsConstructor
@Slf4j
public class ChatStreamController {

    private final ChatStreamOrchestrator orchestrator;
    private final ChatStreamRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * SSE 流式聊天。
     *
     * @param body     请求体，不允许为 null
     * @param response  响应，不允许为 null
     * @return SseEmitter 对象
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        // 1. 同步准备：归属/权限错误在此抛出，返回标准 JSON（尚未声明 event-stream）
        PreparedChat p = orchestrator.prepare(body);

        // 0L = 不超时
        SseEmitter emitter = new SseEmitter(0L);
        OutputStream os;
        try {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            os = response.getOutputStream();
        } catch (Exception e) {
            throw new IllegalStateException("无法建立 SSE 输出流", e);
        }

        // 2. 帧槽：写裸 data 字节；结束时完成 emitter
        SseChatFrameSink sink = new SseChatFrameSink(objectMapper, os, () -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        registry.register(p.getSessionId(), p.getMemoryUser(), sink);
        registerCleanup(emitter, p.getSessionId());

        // 3. 异步产出帧
        orchestrator.execute(p, sink);
        return emitter;
    }

    /**
     * 人工确认（human-in-the-loop）恢复：前端对计划批准/拒绝后调用，
     * 携带 replyId 与各工具决策，Agent 从暂停处继续。
     *
     * @param body     请求体（sessionId/replyId/modelId/characterId/decisions）
     * @param response HTTP 响应
     * @return SSE 发射器
     */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@RequestBody(required = false) Map<String, Object> body,
                             HttpServletResponse response) {
        Map<String, Object> safeBody = body != null ? body : new java.util.LinkedHashMap<>();
        PreparedChat p = orchestrator.prepareResume(safeBody);

        SseEmitter emitter = new SseEmitter(0L);
        OutputStream os;
        try {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            os = response.getOutputStream();
        } catch (Exception e) {
            throw new IllegalStateException("无法建立 SSE 输出流", e);
        }

        SseChatFrameSink sink = new SseChatFrameSink(objectMapper, os, () -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        registry.register(p.getSessionId(), p.getMemoryUser(), sink);
        registerCleanup(emitter, p.getSessionId());

        orchestrator.executeResume(p, sink);
        return emitter;
    }

    /**
     * 停止指定会话的流式输出。
     *
     * @param sessionId 会话ID，不允许为 null
     * @return 结果
     */
    @PostMapping("/stream/{sessionId}/stop")
    public Map<String, Object> stop(@PathVariable String sessionId) {
        registry.requestStop(sessionId, AiUserContext.currentUserId());
        return Map.of("success", true);
    }

    /**
     * 注册连接清理：完成/超时/错误时从注册表移除。
     *
     * @param emitter   发射器
     * @param sessionId 会话标识
     */
    private void registerCleanup(SseEmitter emitter, String sessionId) {
        emitter.onCompletion(() -> registry.remove(sessionId));
        emitter.onTimeout(() -> {
            registry.remove(sessionId);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(t -> registry.remove(sessionId));
    }
}
