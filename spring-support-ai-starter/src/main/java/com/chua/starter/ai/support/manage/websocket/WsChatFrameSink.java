package com.chua.starter.ai.support.manage.websocket;

import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.starter.ai.support.stream.ChatFrameSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket 聊天帧槽：把帧作为 {@code chat.frame} 文本消息发送。
 *
 * <p>消息结构 {@code {event:"chat.frame", data:{chatRequestId, type, payload}}}，
 * type 为 event / trace / done，前端按 chatRequestId 路由到对应生成器。</p>
 *
 * <p>{@link #close()} 为 no-op：{@code /ws/chat} 是多路复用长连接，不能因一轮结束而关闭。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class WsChatFrameSink implements ChatFrameSink {

    /**
     * WS 会话
     */
    private final WebSocketSession session;

    /**
     * 前端请求标识
     */
    private final String chatRequestId;

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 取消标记
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 构造。
     *
     * @param session       WS 会话，不允许为 null
     * @param chatRequestId 前端请求标识，不允许为 null
     * @param objectMapper  JSON 工具，不允许为 null
     */
    public WsChatFrameSink(WebSocketSession session, String chatRequestId, ObjectMapper objectMapper) {
        this.session = session;
        this.chatRequestId = chatRequestId;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendEvent(Map<String, Object> event) {
        sendFrame("event", event);
    }

    @Override
    public void sendTrace(AgentTrace trace) {
        if (trace != null) {
            sendFrame("trace", trace);
        }
    }

    @Override
    public void sendDone() {
        sendFrame("done", null);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void markCancelled() {
        cancelled.set(true);
    }

    @Override
    public void close() {
        // 共享长连接，不关闭
    }

    /**
     * 发送一帧 chat.frame。
     *
     * @param type    event / trace / done
     * @param payload 负载，done 时为 null
     */
    private void sendFrame(String type, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chatRequestId", chatRequestId);
            data.put("type", type);
            if (payload != null) {
                data.put("payload", payload);
            }
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event", "chat.frame");
            envelope.put("data", data);
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(envelope));
            // WebSocketSession.sendMessage 非线程安全，需加锁
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (Exception e) {
            log.debug("[chat-ws] 发送帧失败: {}", e.getMessage());
        }
    }
}
