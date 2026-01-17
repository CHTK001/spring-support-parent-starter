package com.chua.socketio.support.session;

import com.chua.socket.support.properties.SocketProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketUser;
import com.corundumstudio.socketio.SocketIOClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Socket.IO 会话实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public class SocketIOSession implements SocketSession {

    private final SocketIOClient client;
    private final SocketProperties properties;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private String clientId;
    private SocketUser user;

    public SocketIOSession(SocketIOClient client, SocketProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String getId() {
        return client.getSessionId().toString();
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
        return client.isChannelOpen();
    }

    @Override
    public void disconnect() {
        client.disconnect();
    }

    @Override
    public void send(String event, Object data) {
        if (client.isChannelOpen()) {
            String msg;
            if (data instanceof String) {
                msg = (String) data;
            } else {
                try {
                    msg = new ObjectMapper().writeValueAsString(data);
                } catch (Exception e) {
                    msg = data.toString();
                }
            }
            client.sendEvent(event, msg);
        }
    }

    @Override
    public void sendBinary(String event, byte[] data) {
        if (client.isChannelOpen()) {
            // Socket.IO 原生支持二进制数据传输
            client.sendEvent(event, data);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConnection() {
        return (T) client;
    }

    /**
     * 获取原生 Socket.IO 客户端
     *
     * @return SocketIOClient
     */
    public SocketIOClient getClient() {
        return client;
    }
}
