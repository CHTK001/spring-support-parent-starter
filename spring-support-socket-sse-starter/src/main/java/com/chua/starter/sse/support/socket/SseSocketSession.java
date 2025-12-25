package com.chua.starter.sse.support.socket;

import com.chua.common.support.json.Json;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketUser;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Socket 会话实现
 * 封装 SseEmitter 为统一的 SocketSession 接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-24
 */
public class SseSocketSession implements SocketSession {

    private final String id;
    private String clientId;
    private SocketUser user;
    private final SseEmitter emitter;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean connected = true;

    public SseSocketSession(String id, SseEmitter emitter) {
        this.id = id;
        this.clientId = id;
        this.emitter = emitter;

        // 设置回调
        emitter.onCompletion(() -> this.connected = false);
        emitter.onTimeout(() -> this.connected = false);
        emitter.onError(e -> this.connected = false);
    }

    @Override
    public String getId() {
        return id;
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
        return connected;
    }

    @Override
    public void disconnect() {
        if (connected) {
            connected = false;
            emitter.complete();
        }
    }

    @Override
    public void send(String event, Object data) {
        if (!connected) {
            return;
        }
        try {
            String jsonData = data instanceof String ? (String) data : Json.toJson(data);
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(jsonData));
        } catch (IOException e) {
            connected = false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConnection() {
        return (T) emitter;
    }

    /**
     * 获取 SseEmitter
     *
     * @return SseEmitter
     */
    public SseEmitter getEmitter() {
        return emitter;
    }
}
