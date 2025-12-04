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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RSocket 会话模板实现
 * 基于 Spring WebSocket 实现，支持多房间配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Slf4j
public class RSocketSessionTemplateImpl extends TextWebSocketHandler implements SocketSessionTemplate {

    private final SocketProperties properties;
    private final List<SocketListener> listeners;
    
    /**
     * 会话缓存：clientId -> sessions
     */
    private final Map<String, List<RSocketSessionImpl>> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * 会话ID到clientId的映射
     */
    private final Map<String, String> sessionClientMap = new ConcurrentHashMap<>();
    
    private volatile boolean running = false;

    public RSocketSessionTemplateImpl(SocketProperties properties, List<SocketListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从 URI 中解析 clientId（如 /ws/webrtc -> webrtc）
        String path = session.getUri() != null ? session.getUri().getPath() : "/";
        String clientId = extractClientId(path);
        
        RSocketSessionImpl rsocketSession = new RSocketSessionImpl(session);
        rsocketSession.setAttribute("clientId", clientId);
        
        sessionCache.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(rsocketSession);
        sessionClientMap.put(session.getId(), clientId);
        
        log.debug("[RSocket] 客户端连接: clientId={}, sessionId={}", clientId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String clientId = sessionClientMap.remove(sessionId);
        
        if (clientId != null) {
            List<RSocketSessionImpl> sessions = sessionCache.get(clientId);
            if (sessions != null) {
                sessions.removeIf(s -> s.getId().equals(sessionId));
            }
        }
        
        log.debug("[RSocket] 客户端断开: clientId={}, sessionId={}, 状态: {}", clientId, sessionId, status);
    }

    /**
     * 从路径中提取 clientId
     * <p>
     * 例如：/ws/webrtc -> webrtc, /ws -> default, / -> default
     * </p>
     */
    private String extractClientId(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "default";
        }
        
        // 移除开头的 /ws 或 / 
        String cleanPath = path.replaceFirst("^/ws/?", "").replaceFirst("^/", "");
        if (cleanPath.isEmpty()) {
            return "default";
        }
        
        // 取第一段作为 clientId
        int slashIndex = cleanPath.indexOf('/');
        return slashIndex > 0 ? cleanPath.substring(0, slashIndex) : cleanPath;
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
            sessionCache.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(impl);
            sessionClientMap.put(impl.getId(), clientId);
        }
        return session;
    }

    @Override
    public void remove(String clientId, SocketSession session) {
        List<RSocketSessionImpl> sessions = sessionCache.get(clientId);
        if (sessions != null && session instanceof RSocketSessionImpl impl) {
            sessions.remove(impl);
            sessionClientMap.remove(impl.getId());
        }
    }

    @Override
    public SocketSession getSession(String sessionId) {
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            for (RSocketSessionImpl session : sessions) {
                if (session.getId().equals(sessionId)) {
                    return session;
                }
            }
        }
        return null;
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        SocketSession session = getSession(sessionId);
        if (session != null) {
            session.send(event, msg);
        }
    }

    @Override
    public void broadcast(String event, String msg) {
        String message = createMessage(event, msg);
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            for (RSocketSessionImpl session : sessions) {
                try {
                    session.sendRaw(message);
                } catch (Exception e) {
                    log.error("[RSocket] 广播消息失败: {}", session.getId(), e);
                }
            }
        }
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            for (RSocketSessionImpl session : sessions) {
                SocketUser user = session.getUser();
                if (user != null && userId.equals(user.getUserId())) {
                    session.send(event, msg);
                }
            }
        }
    }

    @Override
    public List<SocketSession> getOnlineSessions() {
        List<SocketSession> result = new LinkedList<>();
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            result.addAll(sessions);
        }
        return result;
    }

    @Override
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        List<RSocketSessionImpl> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        if (roomId == null || roomId.isEmpty()) {
            return new LinkedList<>(sessions);
        }

        // 根据 roomId 过滤会话
        List<SocketSession> result = new LinkedList<>();
        for (RSocketSessionImpl session : sessions) {
            Object sessionRoomId = session.getAttribute("roomId");
            if (roomId.equals(sessionRoomId)) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public List<SocketUser> getOnlineUsers(String type) {
        List<SocketUser> result = new LinkedList<>();
        List<String> userIds = new LinkedList<>();
        
        List<RSocketSessionImpl> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        for (RSocketSessionImpl session : sessions) {
            SocketUser user = session.getUser();
            if (user != null && !userIds.contains(user.getUserId())) {
                result.add(user);
                userIds.add(user.getUserId());
            }
        }
        return result;
    }

    @Override
    public int getOnlineCount() {
        int count = 0;
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            count += sessions.size();
        }
        return count;
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
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            for (RSocketSessionImpl session : sessions) {
                try {
                    session.disconnect();
                } catch (Exception e) {
                    log.error("[RSocket] 关闭会话失败: {}", session.getId(), e);
                }
            }
        }
        sessionCache.clear();
        sessionClientMap.clear();
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
