package com.chua.rsocket.support.session;

import com.chua.socket.support.session.SocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.socket.support.session.SocketUser;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSocket 会话实现
 * 基于 Spring WebSocket
 *
 * @version 1.0.0
 * @since 2024-12-04
 * @author CH
 */
@Slf4j
public class RSocketSessionImpl implements SocketSession {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(RSocketSessionImpl.class);
    /** Objectmapper */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    /** 会话 */
    private final WebSocketSession session;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    /** 客户端ID */
    private String clientId;
    /** 用户 */
    private SocketUser user;

    /**
     * 创建 RSocketSessionImpl 实例
     * @param session session
     */
    public RSocketSessionImpl(WebSocketSession session) {
        this.session = session;
    }

    @Override
    /** 获取Id */
    public String getId() {
        return session.getId();
    }

    @Override
    /** 获取ClientId */
    public String getClientId() {
        return clientId;
    }

    @Override
    /** 设置ClientId */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    /** 获取User */
    public SocketUser getUser() {
        return user;
    }

    @Override
    /** 设置User */
    public void setUser(SocketUser user) {
        this.user = user;
    }

    @Override
    /** 设置Attribute */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    /** 获取Attribute */
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    /** 移除Attribute */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    /** 获取Attributes */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    /** 是否Connected */
    public boolean isConnected() {
        return session.isOpen();
    }

    @Override
    /** 断开 */
    public void disconnect() {
        try {
            session.close();
        } catch (IOException e) {
            log.error("[RSocket] 关闭会话失败: {}", getId(), e);
        }
    }

    @Override
    /** 发送 */
    public void send(String event, Object data) {
        if (!session.isOpen()) {
            log.warn("[RSocket] 会话已关闭，无法发送消息: {}", getId());
            return;
        }

        try {
            String message = createMessage(event, data);
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("[RSocket] 发送消息失败: {}", getId(), e);
        }
    }

    /**
     * 发送原始消息
     *
     * @param message 消息内容
     */
    public void sendRaw(String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("[RSocket] 发送消息失败: {}", getId(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /** 获取Connection */
    public <T> T getConnection() {
        return (T) session;
    }

    /**
     * 创建消息
     */
    private String createMessage(String event, Object data) {
        try {
            String dataStr = data instanceof String ? (String) data : objectMapper.writeValueAsString(data);
            Map<String, Object> msg = Map.of(
                    "event", event,
                    "data", dataStr,
                    "timestamp", System.currentTimeMillis()
            );
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("[RSocket] 创建消息失败", e);
            return "{}";
        }
    }
}
