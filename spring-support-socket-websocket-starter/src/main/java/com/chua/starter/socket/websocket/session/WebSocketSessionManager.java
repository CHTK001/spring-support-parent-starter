package com.chua.starter.socket.websocket.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
public class WebSocketSessionManager {

    /**
     * 所有会话：sessionId -> session
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 用户会话映射：userId -> sessionId
     */
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    /**
     * 会话用户映射：sessionId -> userId
     */
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    /**
     * 添加会话
     */
    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.debug("[WebSocket] 会话已添加: {}", session.getId());
    }

    /**
     * 移除会话
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        String userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            userSessions.remove(userId);
        }
        
        log.debug("[WebSocket] 会话已移除: {}", sessionId);
    }

    /**
     * 绑定用户
     */
    public void bindUser(String sessionId, String userId) {
        // 移除旧绑定
        String oldSessionId = userSessions.get(userId);
        if (oldSessionId != null && !oldSessionId.equals(sessionId)) {
            sessionUsers.remove(oldSessionId);
        }
        
        userSessions.put(userId, sessionId);
        sessionUsers.put(sessionId, userId);
        log.debug("[WebSocket] 用户绑定: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 解绑用户
     */
    public void unbindUser(String userId) {
        String sessionId = userSessions.remove(userId);
        if (sessionId != null) {
            sessionUsers.remove(sessionId);
        }
    }

    /**
     * 获取会话
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取用户会话
     */
    public WebSocketSession getSessionByUserId(String userId) {
        String sessionId = userSessions.get(userId);
        return sessionId != null ? sessions.get(sessionId) : null;
    }

    /**
     * 获取会话绑定的用户
     */
    public String getUserId(String sessionId) {
        return sessionUsers.get(sessionId);
    }

    /**
     * 获取所有会话
     */
    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * 获取在线会话数
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 关闭会话
     */
    public void closeSession(String sessionId, CloseStatus status) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.warn("[WebSocket] 关闭会话失败: {}", sessionId, e);
            }
        }
    }

    /**
     * 关闭所有会话
     */
    public void closeAllSessions(CloseStatus status) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close(status);
                }
            } catch (IOException e) {
                log.warn("[WebSocket] 关闭会话失败: {}", session.getId(), e);
            }
        });
        sessions.clear();
        userSessions.clear();
        sessionUsers.clear();
    }
}
