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
 * @since 2024-12-04
 * @author CH
 */
public class SocketIOSession implements SocketSession {

    /**
     * 对象_映射器
    */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * Attribute_encrypt_请求
    */
    public static final String ATTRIBUTE_ENCRYPT_REQUESTED = "socketEncryptRequested";
    /**
     * 客户端
    */
    private final SocketIOClient client;
    /**
     * 属性
    */
    private final SocketProperties properties;
    /**
     * 消息codec
    */
    private final SocketMessageCodec messageCodec;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>(); // attributes
    /**
     * 客户端标识
    */
    private String clientId;
    /**
     * 用户
    */
    private SocketUser user;

    /**
     * 创建 Socketio会话 实例
     * @param client 客户端
     * @param properties Socket属性
     * @param properties 属性
     */
    public SocketIOSession(SocketIOClient client, SocketProperties properties) {
        this.client = client;
        this.properties = properties;
        this.messageCodec = new SocketMessageCodec(properties);
        Boolean encryptRequested = resolveEncryptRequested(client);
        if (encryptRequested != null) {
            /**
             * 设置attribute。
             * @param ATTRIBUTE_ENCRYPT_REQUESTED attributeencrypt请求
             * @param encryptRequested encrypt请求
             */
            setAttribute(ATTRIBUTE_ENCRYPT_REQUESTED, encryptRequested);
        }
    }

    @Override
    /**
     * 获取标识
    */
    public String getId() {
        return client.getSessionId().toString();
    }

    @Override
    /**
     * 获取客户端标识
    */
    public String getClientId() {
        return clientId;
    }

    @Override
    /**
     * 设置客户端标识
    */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    /**
     * 获取用户
    */
    public SocketUser getUser() {
        return user;
    }

    @Override
    /**
     * 设置用户
    */
    public void setUser(SocketUser user) {
        this.user = user;
    }

    @Override
    /**
     * 设置Attribute
    */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * 获取Attribute
     *
     * @param key Key
     * @return 获取attribute的结果
     */
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    /**
     * 移除Attribute
    */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    /**
     * 获取Attributes
    */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    /**
     * 是否连接
    */
    public boolean isConnected() {
        return client.isChannelOpen();
    }

    @Override
    /**
     * 断开
    */
    public void disconnect() {
        client.disconnect();
    }

    @Override
    /**
     * 发送
    */
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
    /**
     * 发送Binary
    */
    public void sendBinary(String event, byte[] data) {
        if (client.isChannelOpen()) {
 // Socket.IO 原生支持二进制数据传输
            client.sendEvent(event, data);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * 获取Connection
     *
     * @return 获取connection的结果
     */
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

    /**
     * 解析encrypt请求
     *
     * @param client 客户端
     * @return resolveencrypt请求的结果
     */
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

    /**
     * 第一个nonblank
     *
     * @param first 第一个
     * @param second second
     * @return 第一个nonblank的结果
     */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    /**
     * 解析encrypt请求
     *
     * @param value 值
     * @return 解析encrypt请求的结果
     */
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
