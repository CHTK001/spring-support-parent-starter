package com.chua.rsocket.support.session;

import com.chua.socket.support.SocketListener;
import com.chua.socket.support.annotations.OnEvent;
import com.chua.socket.support.handler.EventListenerProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * rSocket 会话模板实现
 * 基于 Spring webSocket 实现，支持多房间配置
 *
 * @since 2024-12-04
 * @author CH
 */
@Slf4j
public class RSocketSessionTemplateImpl extends TextWebSocketHandler implements SocketSessionTemplate {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(RSocketSessionTemplateImpl.class);
    /**
     * Objectmapper
    */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 属性
    */
    private final SocketProperties properties;
    /**
     * 监听器
    */
    private final List<SocketListener> listeners;

    /**
     * 事件监听器处理器（扫描 @on事件 注解并分发事件）
     */
    private final EventListenerProcessor eventListenerProcessor;
    
    /**
     * 会话缓存：客户端标识 -> 会话
     */
    private final Map<String, List<RSocketSessionImpl>> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * 会话标识到客户端标识的映射
     */
    private final Map<String, String> sessionClientMap = new ConcurrentHashMap<>();
    
    private volatile boolean running = false; // running

    /**
     * 创建 rSocket会话templateimpl 实例
     * @param properties 属性
     * @param listeners 列表
     * @param listeners 监听器
     */
    public RSocketSessionTemplateImpl(SocketProperties properties, List<SocketListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
        this.eventListenerProcessor = new EventListenerProcessor(listeners);
    }

    @Override
    /**
     * 之后connectionestablished
     * @param session 参数
     * @return 返回值
     * @throws Exception 异常
    */
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
 // 从 URI 中解析 客户端id（如 /ws/webrtc -> webrtc）
        String path = session.getUri() != null ? session.getUri().getPath() : "/";
        String clientId = extractClientId(path);
        
        RSocketSessionImpl rsocketSession = new RSocketSessionImpl(session);
        rsocketSession.setAttribute("clientId", clientId);
        
        sessionCache.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(rsocketSession);
        sessionClientMap.put(session.getId(), clientId);
        
        log.debug("[RSocket] 客户端连接: clientId={}, sessionId={}", clientId, session.getId());
    }

    @Override
    /**
     * 之后connection关闭
     * @param session 参数
     * @param status 参数
     * @return 返回值
     * @throws Exception 异常
    */
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
     * 从路径中提取 客户端标识
     * <p>
     * 例如：/ws/WebRTC -> WebRTC, /ws -> 默认, / -> 默认
     * </p>
     * @param path 路径
     * @return extract客户端id的结果
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
        
 // 取第一段作为 客户端标识
        int slashIndex = cleanPath.indexOf('/');
        return slashIndex > 0 ? cleanPath.substring(0, slashIndex) : cleanPath;
    }

    @Override
    /**
     * 处理文本消息
     * @param message 参数
     * @param session 参数
     * @return 返回值
     * @throws Exception 异常
    */
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("[RSocket] 收到消息: {}", payload);
        
        // 解析消息并触发事件
        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String event = (String) msg.get("event");
            Object data = msg.get("data");
            
 // 通过 事件监听器处理器 调用 @on事件 注解对应的事件处理方法
            RSocketSessionImpl socketSession = (RSocketSessionImpl) getSession(session.getId());
            if (socketSession != null && event != null) {
                eventListenerProcessor.fireEvent(event, socketSession, data);
            } else {
                log.debug("[RSocket] 未找到会话或事件名为空，跳过分发: sessionId={}, event={}", session.getId(), event);
            }
            
        } catch (Exception e) {
            log.error("[RSocket] 消息解析失败: {}", payload, e);
        }
    }

    @Override
    /**
     * 保存
     * @param clientId 字符串
     * @param session 参数
     * @return 返回值
    */
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof RSocketSessionImpl impl) {
            sessionCache.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(impl);
            sessionClientMap.put(impl.getId(), clientId);
        }
        return session;
    }

    @Override
    /**
     * 移除
     * @param clientId 字符串
     * @param session 参数
     * @return 返回值
    */
    public void remove(String clientId, SocketSession session) {
        List<RSocketSessionImpl> sessions = sessionCache.get(clientId);
        if (sessions != null && session instanceof RSocketSessionImpl impl) {
            sessions.remove(impl);
            sessionClientMap.remove(impl.getId());
            // 如果列表为空，清理空列表以节省内存
            if (sessions.isEmpty()) {
                sessionCache.remove(clientId);
            }
        }
    }

    @Override
    /**
     * 获取会话
     * @param sessionId 字符串
     * @return 返回值
    */
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
    /**
     * 发送
     * @param event 字符串
     * @param msg 字符串
     * @param sessionId 字符串
     * @return 返回值
    */
    public void send(String sessionId, String event, String msg) {
        SocketSession session = getSession(sessionId);
        if (session != null) {
            session.send(event, msg);
        }
    }

    @Override
    /**
     * Broadcast
     * @param event 字符串
     * @param msg 字符串
     * @return 返回值
    */
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
    /**
     * 发送转为用户
     * @param event 字符串
     * @param msg 字符串
     * @param userId 字符串
     * @return 返回值
    */
    public void sendToUser(String userId, String event, String msg) {
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            for (RSocketSessionImpl session : sessions) {
                SocketUser user = session.getUser();
                if (user != null && userId.equals(user.getUserId())) {
                    try {
                        session.send(event, msg);
                    } catch (Exception e) {
                        log.warn("[RSocket] 发送消息给用户失败: userId={}, sessionId={}, event={}", 
                                userId, session.getId(), event, e);
                    }
                }
            }
        }
    }

    @Override
    /**
     * 获取online会话
     * @return 返回值
    */
    public List<SocketSession> getOnlineSessions() {
        List<SocketSession> result = new java.util.ArrayList<>();
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            result.addAll(sessions);
        }
        return result;
    }

    @Override
    /**
     * 获取online会话
     * @param roomId 字符串
     * @param type 字符串
     * @return 返回值
    */
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        List<RSocketSessionImpl> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        if (roomId == null || roomId.isEmpty()) {
            return new java.util.ArrayList<>(sessions);
        }

 // 根据 roomid 过滤会话
        List<SocketSession> result = new java.util.ArrayList<>();
        for (RSocketSessionImpl session : sessions) {
            Object sessionRoomId = session.getAttribute("roomId");
            if (roomId.equals(sessionRoomId)) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    /**
     * 获取online用户
     * @param type 字符串
     * @return 返回值
    */
    public List<SocketUser> getOnlineUsers(String type) {
        List<SocketUser> result = new java.util.ArrayList<>();
        java.util.Set<String> userIds = new java.util.HashSet<>();
        
        List<RSocketSessionImpl> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        for (RSocketSessionImpl session : sessions) {
            SocketUser user = session.getUser();
            if (user != null && userIds.add(user.getUserId())) {
                result.add(user);
            }
        }
        return result;
    }

    @Override
    /**
     * 获取Online计算数量
     * @return 返回值
    */
    public int getOnlineCount() {
        int count = 0;
        for (List<RSocketSessionImpl> sessions : sessionCache.values()) {
            count += (int) sessions.stream()
                    .filter(SocketSession::isConnected)
                    .count();
        }
        return count;
    }

    @Override
    /**
     * 开始
    */
    public void start() {
        if (running) {
            log.warn("[RSocket] 服务已启动");
            return;
        }
        running = true;
        log.info("[RSocket] 服务启动成功");
    }

    @Override
    /**
     * 停止
    */
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
     * @param event 事件
     * @param data 数据
     * @return 创建消息的结果
     */
    private String createMessage(String event, Object data) {
        try {
            Map<String, Object> msg = Map.of(
                    "event", event,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            );
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("[RSocket] 创建消息失败", e);
            return "{}";
        }
    }
}
