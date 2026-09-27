package com.chua.starter.ai.support.manage.websocket;

import com.chua.starter.ai.support.util.AiUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
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
 * <p>服务端主动推送一律按归属投递：带归属的用户态事件（会话变更、终端输出、伙伴配置等）经
 * {@link #sendToUser(String, String, Object)} 只投给该登录用户的在线连接，身份取不到则不发。</p>
 *
 * <p>连接归属：{@code /ws} 的升级请求由 {@code AiWebSocketHandshakeInterceptor} 校验登录令牌，
 * 归属用户写在握手属性里；校验不通过则直接拒绝握手，避免匿名连接收到他人事件。
 * 认证未启用（单机/开发形态）时没有用户维度，连接绑定到 {@link #AUTH_DISABLED_OWNER} 通道。</p>
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
     * 认证未启用时的连接归属通道（单机/开发形态没有用户维度，握手与推送都用它对齐）
     */
    public static final String AUTH_DISABLED_OWNER = "__auth_disabled__";

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
        String userId = ownerOf(session);
        if (userId == null) {
            log.warn("[nomifun-ws] 连接未绑定登录用户，已拒绝: sessionId={}", session.getId());
            closeQuietly(session);
            return;
        }
        sessions.put(session.getId(), session);
        log.info("[nomifun-ws] 连接建立: sessionId={}, userId={}", session.getId(), userId);
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
                        "data", result == null ? Map.of() : result
                );
                sendSafe(session, response);
            }
        } catch (Exception e) {
            log.error("[nomifun-ws] 处理消息异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 分发订阅命令。
     *
     * @param cmd 命令名，去除订阅前缀后的内容
     * @param requestData 请求数据，允许为空
     * @return 命令执行结果；当前未注册命令时返回明确的错误信息
     */
    private Object dispatch(String cmd, JsonNode requestData) {
        log.debug("[nomifun-ws] 收到订阅命令: {}", cmd);
        return Map.of(
                "supported", false,
                "error", "未注册的订阅命令: " + cmd
        );
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session.getId());
        log.info("[nomifun-ws] 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    /**
     * 只向某个登录用户的在线连接推送事件。
     *
     * <p>投递必须带归属：身份取不到时不发给任何人，避免把他人数据推给所有客户端。</p>
     *
     * @param userId 归属用户标识，为空则丢弃（不给任何人发）
     * @param event  事件名
     * @param data   事件数据
     */
    public void sendToUser(String userId, String event, Object data) {
        if (userId == null || userId.isBlank()) {
            log.warn("[nomifun-ws] 事件缺少归属用户，已丢弃: event={}", event);
            return;
        }
        TextMessage message = toMessage(event, data);
        if (message == null) {
            return;
        }
        for (WebSocketSession session : new ArrayList<>(sessions.values())) {
            if (userId.equals(ownerOf(session))) {
                sendSafe(session, message);
            }
        }
    }

    /**
     * 序列化 {@code {name, data}} 事件信封。
     *
     * @param event 事件名
     * @param data  事件数据
     * @return 文本消息，序列化失败返回 {@code null}
     */
    private TextMessage toMessage(String event, Object data) {
        String text;
        try {
            text = objectMapper.writeValueAsString(Map.of("name", event, "data", data));
        } catch (Exception e) {
            log.error("[nomifun-ws] 序列化推送失败: {}", e.getMessage(), e);
            return null;
        }
        return new TextMessage(text);
    }

    /**
     * 连接绑定的归属用户。
     *
     * @param session 在线 会话
     * @return 归属用户标识，未绑定返回 {@code null}
     */
    private String ownerOf(WebSocketSession session) {
        Object owner = session.getAttributes().get(AiUserContext.USER_ID_ATTRIBUTE);
        return owner == null || owner.toString().isBlank() ? null : owner.toString();
    }

    /**
     * 静默关闭连接。
     *
     * @param session 在线 会话
     */
    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("[nomifun-ws] 关闭未授权连接失败: {}", e.getMessage());
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
