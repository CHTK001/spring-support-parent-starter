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
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Slf4j
public class RSocketSessionImpl implements SocketSession {

    private static final Logger log = LoggerFactory.getLogger(RSocketSessionImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketSession session;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private String clientId;
    private SocketUser user;

    public RSocketSessionImpl(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public SocketUser getUser() {
        return user;
    }

    @Override
    public void setUser(SocketUser user) {
        this.user = user;
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean isConnected() {
        return session.isOpen();
    }

    @Override
    public void disconnect() {
        try {
            session.close();
        } catch (IOException e) {
            log.error("[RSocket] 关闭会话失败: {}", getId(), e);
        }
    }

    @Override
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
