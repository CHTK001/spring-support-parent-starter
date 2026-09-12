package com.chua.starter.ai.support.manage.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
* Nomifun 前端 /ws 协议处理器。
*
* <p>前端 {@code platform/bridge.ts} 的 pub/sub RPC 契约（与 Rust 后端保持字节级兼容）：
* 客户端发 {@code {name:"subscribe-<cmd>", data:{id, data}}}，服务端回
* {@code {name:"subscribe.callback-<cmd><id>", data:result}}。</p>
*
* <p>服务端主动推送使用任意事件名（如 {@code message.stream}、{@code turn.completed}），
* 供 REST 控制器在业务处理后经 {@link #broadcast(String, Object)} 下发。</p>
*
* <p>心跳：每 20s 向所有在线连接发送 {@code {name:"ping"}}，前端收到后回 {@code pong}（服务端忽略）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysAiWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    @Override
    public List<String> getSubProtocols() {
 // 桌面模式使用 nomi-本地-信任，Web 模式使用 对话，均需支持
        return List.of("nomi-local-trust", "chat");
    }

    /**
    * 心跳间隔（毫秒），前端要求 ≤30s 至少一次
     */
    private static final long HEARTBEAT_INTERVAL_MS = 20_000L;

    /**
    * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
    * 在线会话
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
    * 心跳调度器
     */
    private ScheduledExecutorService heartbeatScheduler;

    @PostConstruct
    /**
    * 启动心跳。
     */
    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nomifun-ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(this::pingAll, HEARTBEAT_INTERVAL_MS,
                HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("[nomifun-ws] 心跳任务已启动，间隔 {}ms", HEARTBEAT_INTERVAL_MS);
    }

    @PreDestroy
    /**
    * 停止心跳。
     */
    private void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("[nomifun-ws] 连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("[nomifun-ws] 收到消息: {}", payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            String name = node.path("name").asText("");
            JsonNode data = node.path("data");
            // 心跳应答：前端对 ping 回 pong，服务端无需处理
            if ("pong".equals(name) || "ping".equals(name)) {
                return;
            }
            if (name.startsWith("subscribe-")) {
                String cmd = name.substring("subscribe-".length());
                String id = data.path("id").asText("");
                JsonNode requestData = data.path("data");
                Object result = dispatch(cmd, requestData);
                Map<String, Object> response = Map.of(
                        "name", "subscribe.callback-" + cmd + id,
                        "data", result == null ? "" : result == null ? "" : result
                );
                /**
                * 发送safe。
                * @param session 会话
                * @param response 响应
                 */
                sendSafe(session, response);
            }
        } catch (Exception e) {
            log.error("[nomifun-ws] 处理消息异常: {}", e.getMessage(), e);
        }
    }

    /**
    * 命令分发：当前为占位实现，后续按契约命令逐组接入自有 服务。
    *
    * @param cmd         命令名（去掉 订阅- 前缀）
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
        log.info("[nomifun-ws] 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    /**
    * 向所有在线会话推送事件。
    *
    * @param event 事件名（如 消息.流）
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
        for (WebSocketSession session : new ArrayList<>(sessions.values())) {
            /**
            * 发送safe。
            * @param session 会话
            * @param message 消息
             */
            sendSafe(session, message);
        }
    }

    /**
    * 向所有在线连接发送心跳。
     */
    private void pingAll() {
        Map<String, Object> ping = Map.of("name", "ping");
        try {
            String text = objectMapper.writeValueAsString(ping);
            TextMessage message = new TextMessage(text);
            List<WebSocketSession> snapshot = new ArrayList<>(sessions.values());
            if (snapshot.isEmpty()) {
                return;
            }
            for (WebSocketSession session : snapshot) {
                /**
                * 发送safe。
                * @param session 会话
                * @param message 消息
                 */
                sendSafe(session, message);
            }
        } catch (Exception e) {
            log.debug("[nomifun-ws] 心跳发送失败: {}", e.getMessage());
        }
    }

    /**
    * 安全发送（会话关闭/异常时忽略）。
    * @param session 会话
    * @param payload payload
     */
    private void sendSafe(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            TextMessage message = payload instanceof TextMessage
                    ? (TextMessage) payload
                    : new TextMessage(objectMapper.writeValueAsString(payload));
            session.sendMessage(message);
        } catch (Exception e) {
            log.debug("[nomifun-ws] 推送失败: {}", e.getMessage());
        }
    }
}
