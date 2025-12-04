package com.chua.rsocket.support.session;

import com.chua.common.support.json.Json;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSocket 会话模板实现
 * 基于 Spring WebSocket 实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Slf4j
public class RSocketSessionTemplateImpl extends TextWebSocketHandler implements SocketSessionTemplate {

    private final SocketProperties properties;
    private final List<SocketListener> listeners;
    private final Map<String, RSocketSessionImpl> sessionCache = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public RSocketSessionTemplateImpl(SocketProperties properties, List<SocketListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        RSocketSessionImpl rsocketSession = new RSocketSessionImpl(session);
        sessionCache.put(session.getId(), rsocketSession);
        log.debug("[RSocket] 客户端连接: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionCache.remove(session.getId());
        log.debug("[RSocket] 客户端断开: {}, 状态: {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("[RSocket] 收到消息: {}", payload);
        
        // 解析消息并触发事件
        try {
            Map<String, Object> msg = Json.fromJson(payload, Map.class);
            String event = (String) msg.get("event");
            Object data = msg.get("data");
            
            // TODO: 通过注解处理器调用对应的事件处理方法
            
        } catch (Exception e) {
            log.error("[RSocket] 消息解析失败: {}", payload, e);
        }
    }

    @Override
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof RSocketSessionImpl impl) {
            sessionCache.put(clientId, impl);
        }
        return session;
    }

    @Override
    public void remove(String clientId, SocketSession session) {
        sessionCache.remove(clientId);
    }

    @Override
    public SocketSession getSession(String sessionId) {
        return sessionCache.get(sessionId);
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        RSocketSessionImpl session = sessionCache.get(sessionId);
        if (session != null) {
            session.send(event, msg);
        }
    }

    @Override
    public void broadcast(String event, String msg) {
        String message = createMessage(event, msg);
        for (RSocketSessionImpl session : sessionCache.values()) {
            try {
                session.sendRaw(message);
            } catch (Exception e) {
                log.error("[RSocket] 广播消息失败: {}", session.getId(), e);
            }
        }
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        for (RSocketSessionImpl session : sessionCache.values()) {
            SocketUser user = session.getUser();
            if (user != null && userId.equals(user.getUserId())) {
                session.send(event, msg);
            }
        }
    }

    @Override
    public List<SocketSession> getOnlineSessions() {
        return new LinkedList<>(sessionCache.values());
    }

    @Override
    public List<SocketUser> getOnlineUsers(String type) {
        List<SocketUser> result = new LinkedList<>();
        List<String> userIds = new LinkedList<>();
        
        for (RSocketSessionImpl session : sessionCache.values()) {
            SocketUser user = session.getUser();
            if (user != null && !userIds.contains(user.getUserId())) {
                if (type == null || type.equals(user.getType())) {
                    result.add(user);
                    userIds.add(user.getUserId());
                }
            }
        }
        return result;
    }

    @Override
    public int getOnlineCount() {
        return sessionCache.size();
    }

    @Override
    public void start() {
        if (running) {
            log.warn("[RSocket] 服务已启动");
            return;
        }
        running = true;
        log.info("[RSocket] 服务启动成功，端口: {}", properties.getPort());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        
        // 关闭所有会话
        for (RSocketSessionImpl session : sessionCache.values()) {
            try {
                session.disconnect();
            } catch (Exception e) {
                log.error("[RSocket] 关闭会话失败: {}", session.getId(), e);
            }
        }
        sessionCache.clear();
        running = false;
        log.info("[RSocket] 服务已停止");
    }

    /**
     * 创建消息
     */
    private String createMessage(String event, Object data) {
        Map<String, Object> msg = Map.of(
                "event", event,
                "data", data,
                "timestamp", System.currentTimeMillis()
        );
        return Json.toJSONString(msg);
    }
}
