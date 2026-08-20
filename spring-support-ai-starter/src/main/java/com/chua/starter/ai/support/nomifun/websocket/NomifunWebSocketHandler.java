package com.chua.starter.ai.support.nomifun.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nomifun 前端 /ws 协议处理器。
 *
 * <p>前端 {@code platform/bridge.ts} 的 pub/sub RPC 契约（与 Rust 后端保持字节级兼容）：</p>
 * <pre>
 *   客户端发：{ "name": "subscribe-&lt;cmd&gt;", "data": { "id": "xxx", "data": ... } }
 *   服务端回：{ "name": "subscribe.callback-&lt;cmd&gt;&lt;id&gt;", "data": &lt;result&gt; }
 * </pre>
 * 服务器主动推送使用任意事件名（如 {@code message.stream}、{@code turn.completed}）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NomifunWebSocketHandler extends TextWebSocketHandler {

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 在线会话
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("[nomifun-ws] 连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("[nomifun-ws] 收到消息: {}", payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            String name = node.path("name").asText("");
            JsonNode data = node.path("data");
            if (name.startsWith("subscribe-")) {
                String cmd = name.substring("subscribe-".length());
                String id = data.path("id").asText("");
                JsonNode requestData = data.path("data");
                Object result = dispatch(cmd, requestData);
                Map<String, Object> response = Map.of(
                        "name", "subscribe.callback-" + cmd + id,
                        "data", result == null ? "" : result
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        } catch (Exception e) {
            log.error("[nomifun-ws] 处理消息异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 命令分发：当前为占位实现，后续按契约命令逐组接入自有 service。
     *
     * @param cmd         命令名（去掉 subscribe- 前缀）
     * @param requestData 请求数据
     * @return 响应数据
     */
    private Object dispatch(String cmd, JsonNode requestData) {
        log.debug("[nomifun-ws] 命令: {}", cmd);
        return "";
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session.getId());
        log.info("[nomifun-ws] 连接关闭: sessionId={}", session.getId());
    }

    /**
     * 向所有在线会话推送事件。
     *
     * @param event 事件名（如 message.stream）
     * @param data  事件数据
     */
    public void broadcast(String event, Object data) {
        String text;
        try {
            text = objectMapper.writeValueAsString(Map.of("name", event, "data", data));
        } catch (Exception e) {
            log.error("[nomifun-ws] 序列化推送失败: {}", e.getMessage(), e);
            return;
        }
        TextMessage message = new TextMessage(text);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (Exception e) {
                    log.debug("[nomifun-ws] 推送失败: {}", e.getMessage());
                }
            }
        }
    }
}
