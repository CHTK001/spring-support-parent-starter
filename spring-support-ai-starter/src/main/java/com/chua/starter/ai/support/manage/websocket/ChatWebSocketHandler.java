package com.chua.starter.ai.support.manage.websocket;

import com.chua.starter.ai.support.stream.ChatStreamOrchestrator;
import com.chua.starter.ai.support.stream.ChatStreamRegistry;
import com.chua.starter.ai.support.stream.PreparedChat;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天 WebSocket 处理器（端点 {@code /ws/chat}）。
 *
 * <p>消息约定（与 @repo/core SocketTemplate 的 {@code {event,data}} 一致）：
 * <ul>
 *   <li>客户端发 {@code {event:"chat.start", data:&lt;聊天请求体&gt;}}，服务端经 {@link WsChatFrameSink}
 *       逐帧回 {@code chat.frame}；</li>
 *   <li>{@code {event:"chat.stop", data:{sessionId}}} 停止；</li>
 *   <li>{@code {event:"ping"}} 回 {@code {event:"pong"}}；</li>
 *   <li>准备阶段错误回 {@code chat.error}。</li>
 * </ul>
 * 连接关闭时取消其下所有轮次。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /**
     * 编排器
     */
    private final ChatStreamOrchestrator orchestrator;

    /**
     * 注册表
     */
    private final ChatStreamRegistry registry;

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * WS 会话 id -> 其下聊天 sessionId 集合
     */
    private final Map<String, Set<String>> wsToChats = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[chat-ws] 连接已建立 id={} remote={}", session.getId(),
                session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            switch (event) {
                case "ping" -> send(session, "pong", new LinkedHashMap<>());
                case "chat.stop" -> handleStop(session, data);
                case "chat.start" -> handleStart(session, data);
                default -> log.debug("[chat-ws] 未识别事件: {}", event);
            }
        } catch (Exception e) {
            log.warn("[chat-ws] 处理消息异常: {}", e.getMessage());
        }
    }

    /**
     * 处理 chat.start。
     *
     * @param session WS 会话
     * @param data    聊天请求体
     */
    @SuppressWarnings("unchecked")
    private void handleStart(WebSocketSession session, JsonNode data) {
        Map<String, Object> body = objectMapper.convertValue(data, Map.class);
        UserResume resume = (UserResume) session.getAttributes().get(AiUserContext.RESUME_ATTRIBUTE);
        AiUserContext.runWith(resume, () -> {
            String chatRequestId = body.get("chatRequestId") != null
                    ? String.valueOf(body.get("chatRequestId")) : null;
            try {
                PreparedChat p = orchestrator.prepare(body);
                WsChatFrameSink sink = new WsChatFrameSink(session, p.chatRequestId(), objectMapper);
                registry.register(p.sessionId(), p.memoryUser(), sink);
                wsToChats.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet())
                        .add(p.sessionId());
                orchestrator.execute(p, sink);
                log.info("[chat-ws] chat.start 已受理 sessionId={} chatRequestId={}",
                        p.sessionId(), p.chatRequestId());
            } catch (Exception e) {
                log.warn("[chat-ws] 准备失败: {}", e.getMessage());
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("chatRequestId", chatRequestId);
                err.put("message", e.getMessage() != null ? e.getMessage() : "聊天准备失败");
                send(session, "chat.error", err);
            }
        });
    }

    /**
     * 处理 chat.stop。
     *
     * @param session WS 会话
     * @param data    {sessionId}
     */
    private void handleStop(WebSocketSession session, JsonNode data) {
        String chatSessionId = data.path("sessionId").asText("");
        UserResume resume = (UserResume) session.getAttributes().get(AiUserContext.RESUME_ATTRIBUTE);
        String actor = resume == null ? null : resume.getUserId();
        registry.requestStop(chatSessionId, actor);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<String> chats = wsToChats.remove(session.getId());
        if (chats != null) {
            UserResume resume = (UserResume) session.getAttributes().get(AiUserContext.RESUME_ATTRIBUTE);
            String actor = resume == null ? null : resume.getUserId();
            for (String chatSessionId : chats) {
                try {
                    registry.requestStop(chatSessionId, actor);
                } catch (Exception ignored) {
                }
            }
        }
        log.info("[chat-ws] 连接关闭: id={}, status={}", session.getId(), status);
    }

    /**
     * 发送一条 {event,data} 文本消息。
     *
     * @param session WS 会话
     * @param event   事件名
     * @param data    数据
     */
    private void send(WebSocketSession session, String event, Object data) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event", event);
            envelope.put("data", data);
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(envelope));
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (Exception e) {
            log.debug("[chat-ws] 发送失败 event={}: {}", event, e.getMessage());
        }
    }
}
