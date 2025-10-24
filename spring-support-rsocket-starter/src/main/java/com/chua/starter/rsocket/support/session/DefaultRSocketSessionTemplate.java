package com.chua.starter.rsocket.support.session;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认RSocket会话模板实现
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Slf4j
public class DefaultRSocketSessionTemplate implements RSocketSessionTemplate {

    /**
     * 会话存储
     * Key: sessionId, Value: RSocketSession
     */
    private final Map<String, RSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 用户会话映射
     * Key: userId, Value: sessionId
     */
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @Override
    public RSocketSession save(String sessionId, RSocketSession session) {
        sessions.put(sessionId, session);
        
        // 如果会话有用户信息，建立用户映射
        RSocketUser user = session.getUser();
        if (user != null && user.getUserId() != null) {
            userSessions.put(user.getUserId(), sessionId);
        }
        
        log.debug("保存RSocket会话: sessionId={}", sessionId);
        return session;
    }

    @Override
    public void remove(String sessionId) {
        RSocketSession session = sessions.remove(sessionId);
        if (session != null) {
            // 移除用户映射
            RSocketUser user = session.getUser();
            if (user != null && user.getUserId() != null) {
                userSessions.remove(user.getUserId());
            }
            
            // 关闭会话
            session.close();
            
            log.debug("移除RSocket会话: sessionId={}", sessionId);
        }
    }

    @Override
    public RSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        RSocketSession session = getSession(sessionId);
        if (session != null) {
            session.send(event, msg);
        } else {
            log.warn("会话不存在，无法发送消息: sessionId={}", sessionId);
        }
    }

    @Override
    public void broadcast(String event, String msg) {
        int count = 0;
        for (RSocketSession session : sessions.values()) {
            try {
                session.send(event, msg);
                count++;
            } catch (Exception e) {
                log.error("广播消息失败: sessionId={}", session.getSessionId(), e);
            }
        }
        log.debug("广播消息完成: event={}, 发送数量={}", event, count);
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        String sessionId = userSessions.get(userId);
        if (sessionId != null) {
            send(sessionId, event, msg);
        } else {
            log.warn("用户不在线，无法发送消息: userId={}", userId);
        }
    }

    @Override
    public int getOnlineCount() {
        return sessions.size();
    }

    @Override
    public void disconnect(String sessionId) {
        remove(sessionId);
    }
}

