package com.chua.starter.socket.websocket.template;

import com.chua.starter.socket.websocket.session.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;

/**
* web套接字 消息模板
* <p>
* 提供便捷的消息发送方法
* </p>
*
* @since 2025-12-25
* @author CH
 */
@Slf4j
public class WebSocketTemplate {

    /** 会话管理器 */
    private final WebSocketSessionManager sessionManager;

    /**
    * 构造器
    *
    * @param sessionManager 会话管理器
     */
    public WebSocketTemplate(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
    * 发送消息到指定会话
    *
    * @param sessionId 会话标识
    * @param message   消息内容
    * @return 是否发送成功
     */
    public boolean send(String sessionId, String message) {
        WebSocketSession session = sessionManager.getSession(sessionId);
        return sendToSession(session, message);
    }

    /**
    * 发送消息到指定用户
    *
    * @param userId  用户标识
    * @param message 消息内容
    * @return 是否发送成功
     */
    public boolean sendToUser(String userId, String message) {
        WebSocketSession session = sessionManager.getSessionByUserId(userId);
        return sendToSession(session, message);
    }

    /**
    * 广播消息到所有会话
    *
    * @param message 消息内容
    * @return 成功发送的数量
     */
    public int broadcast(String message) {
        Collection<WebSocketSession> sessions = sessionManager.getAllSessions();
        int count = 0;
        for (WebSocketSession session : sessions) {
            if (sendToSession(session, message)) {
                count++;
            }
        }
        return count;
    }

    /**
    * 绑定用户到当前会话
    *
    * @param sessionId 会话标识
    * @param userId    用户标识
     */
    public void bindUser(String sessionId, String userId) {
        sessionManager.bindUser(sessionId, userId);
    }

    /**
    * 解绑用户
    *
    * @param userId 用户标识
     */
    public void unbindUser(String userId) {
        sessionManager.unbindUser(userId);
    }

    /**
    * 获取在线会话数
    *
    * @return 会话数量
     */
    public int getOnlineCount() {
        return sessionManager.getSessionCount();
    }

    /**
    * 发送消息到会话
    * @param session 会话
    * @param message 消息
    * @return 发送转为会话的结果
     */
    private boolean sendToSession(WebSocketSession session, String message) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
            return true;
        } catch (IOException e) {
            log.error("[WebSocket] 发送消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }
}

