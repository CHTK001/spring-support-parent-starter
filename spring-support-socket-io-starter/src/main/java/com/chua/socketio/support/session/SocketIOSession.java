package com.chua.socketio.support.session;

import com.chua.socket.support.codec.SocketMessageCodec;
import com.chua.socket.support.properties.SocketProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketUser;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;

import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Socket.IO 会话实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public class SocketIOSession implements SocketSession {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String ATTRIBUTE_ENCRYPT_REQUESTED = "socketEncryptRequested";
    private final SocketIOClient client;
    private final SocketProperties properties;
    private final SocketMessageCodec messageCodec;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private String clientId;
    private SocketUser user;

    public SocketIOSession(SocketIOClient client, SocketProperties properties) {
        this.client = client;
        this.properties = properties;
        this.messageCodec = new SocketMessageCodec(properties);
        Boolean encryptRequested = resolveEncryptRequested(client);
        if (encryptRequested != null) {
            setAttribute(ATTRIBUTE_ENCRYPT_REQUESTED, encryptRequested);
        }
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
                    msg = OBJECT_MAPPER.writeValueAsString(data);
                } catch (Exception e) {
                    msg = data.toString();
                }
            }
            client.sendEvent(event, messageCodec.encode(msg, getAttribute(ATTRIBUTE_ENCRYPT_REQUESTED)));
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

    private Boolean resolveEncryptRequested(SocketIOClient client) {
        if (client == null) {
            return null;
        }
        HandshakeData handshakeData = client.getHandshakeData();
        if (handshakeData == null) {
            return null;
        }
        String encryptValue = firstNonBlank(
                handshakeData.getSingleUrlParam("socketEncrypt"),
                handshakeData.getSingleUrlParam("encrypt"));
        return parseEncryptRequested(encryptValue);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private Boolean parseEncryptRequested(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on", "encrypt", "encrypted" -> true;
            case "0", "false", "no", "off", "plain", "plaintext" -> false;
            default -> null;
        };
    }
}
