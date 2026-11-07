package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.stream.ChatFrameSink;
import com.chua.starter.ai.support.stream.ChatStreamOrchestrator;
import com.chua.starter.ai.support.stream.ChatStreamRegistry;
import com.chua.starter.ai.support.stream.IdleTimeoutChatFrameSink;
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
 * @since 4.0.0.51
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
     * 全局设置读取器（用户覆盖优先）：解析会话超时时间
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 会话超时配置所在分组（设置中心 → 会话配置）
     */
    private static final String GROUP_SESSION = "session";

    /**
     * 会话超时配置字段名
     */
    private static final String FIELD_STREAM_IDLE_TIMEOUT = "stream_idle_timeout_minutes";

    /**
     * 会话超时兜底值（分钟）：配置缺失/非法时用这个
     */
    private static final int DEFAULT_IDLE_TIMEOUT_MINUTES = 30;

    /**
     * 会话超时可配置区间下限（分钟）
     */
    private static final int MIN_IDLE_TIMEOUT_MINUTES = 1;

    /**
     * 会话超时可配置区间上限（分钟）
     */
    private static final int MAX_IDLE_TIMEOUT_MINUTES = 180;

    /**
     * 给帧槽套上「会话超时」看门狗。
     *
     * <p>超时时长按<b>发起本轮的用户</b>解析（该用户的覆盖优先、全局兜底），
     * 因此每个用户可以有自己的超时时长。配置为 0 表示不限制，此时原样返回、不计时。</p>
     *
     * @param sink      真实帧槽，不允许为 null
     * @param sessionId 会话标识
     * @param userId    发起本轮的用户标识，可为空
     * @return 带看门狗的帧槽（已启动计时）
     */
    private ChatFrameSink withIdleTimeout(ChatFrameSink sink, String sessionId, String userId) {
        int minutes = resolveIdleTimeoutMinutes(userId);
        ChatFrameSink wrapped = IdleTimeoutChatFrameSink.wrap(sink, sessionId, minutes);
        if (wrapped instanceof IdleTimeoutChatFrameSink watchdog) {
            watchdog.start();
        }
        return wrapped;
    }

    /**
     * 解析该用户生效的会话超时时长（分钟）。
     *
     * @param userId 用户标识，可为空（此时只读全局层）
     * @return 超时分钟数；0 表示不限制
     */
    private int resolveIdleTimeoutMinutes(String userId) {
        String raw = globalSettingReader.getStrAs(userId, GROUP_SESSION, FIELD_STREAM_IDLE_TIMEOUT);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_IDLE_TIMEOUT_MINUTES;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("[ChatStream] {} 非整数，回落默认值: {}", FIELD_STREAM_IDLE_TIMEOUT, raw);
            return DEFAULT_IDLE_TIMEOUT_MINUTES;
        }
        if (minutes <= 0) {
            // 0 或负数 = 显式关闭超时：不计时，保持原有行为
            return 0;
        }
        return Math.min(Math.max(minutes, MIN_IDLE_TIMEOUT_MINUTES), MAX_IDLE_TIMEOUT_MINUTES);
    }

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
            } catch (Exception e) {
                log.debug("[ai] 操作失败，已忽略: {}", e.getMessage());
            }
        });
        // 2.5 帧槽套上「会话超时」看门狗后再注册：
        // registry 存的是带看门狗的槽，「停止生成」与超时收口才会作用在同一层，
        // 否则取消会绕过看门狗、直接关掉真实传输，留下一个还在跑的看门狗。
        ChatFrameSink guarded = withIdleTimeout(sink, p.sessionId(), p.memoryUser());
        registry.register(p.sessionId(), p.memoryUser(), guarded);
        registerCleanup(emitter, p.sessionId());

        // 3. 异步产出帧
        orchestrator.execute(p, guarded);
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
            } catch (Exception e) {
                log.debug("[ai] 操作失败，已忽略: {}", e.getMessage());
            }
        });
        registry.register(p.sessionId(), p.memoryUser(), sink);
        registerCleanup(emitter, p.sessionId());

        // 恢复轮次同样套会话超时看门狗（注册的是带看门狗的槽，取消/停止才会作用在真实传输上）
        ChatFrameSink guardedResume = withIdleTimeout(sink, p.sessionId(), p.memoryUser());
        registry.register(p.sessionId(), p.memoryUser(), guardedResume);
        orchestrator.executeResume(p, guardedResume);
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
            } catch (Exception e) {
                log.debug("[ai] 操作失败，已忽略: {}", e.getMessage());
            }
        });
        emitter.onError(t -> registry.remove(sessionId));
    }
}
