package com.chua.rsocket.support.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSocket 会话抽象
 * 封装客户端连接信息和属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public class RSocketSession {

    /**
     * 会话ID
     */
    private final String id;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 用户信息
     */
    private RSocketUser user;

    /**
     * 会话属性
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 连接时间
     */
    private final long connectTime;

    /**
     * 最后活动时间
     */
    private long lastActiveTime;

    /**
     * 是否已连接
     */
    private volatile boolean connected = true;

    /**
     * 底层 RSocket 连接
     */
    private Object requester;

    public RSocketSession() {
        this.id = UUID.randomUUID().toString();
        this.connectTime = System.currentTimeMillis();
        this.lastActiveTime = this.connectTime;
    }

    public RSocketSession(String id) {
        this.id = id;
        this.connectTime = System.currentTimeMillis();
        this.lastActiveTime = this.connectTime;
    }

    /**
     * 获取会话ID
     *
     * @return 会话ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取客户端ID
     *
     * @return 客户端ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置客户端ID
     *
     * @param clientId 客户端ID
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息
     */
    public RSocketUser getUser() {
        return user;
    }

    /**
     * 设置用户信息
     *
     * @param user 用户信息
     */
    public void setUser(RSocketUser user) {
        this.user = user;
    }

    /**
     * 设置属性
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 获取属性
     *
     * @param key 属性键
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 移除属性
     *
     * @param key 属性键
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    /**
     * 获取所有属性
     *
     * @return 属性Map
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 获取连接时间
     *
     * @return 连接时间戳
     */
    public long getConnectTime() {
        return connectTime;
    }

    /**
     * 获取最后活动时间
     *
     * @return 最后活动时间戳
     */
    public long getLastActiveTime() {
        return lastActiveTime;
    }

    /**
     * 更新最后活动时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 是否已连接
     *
     * @return 是否已连接
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 标记为断开连接
     */
    public void disconnect() {
        this.connected = false;
    }

    /**
     * 获取底层RSocket连接
     *
     * @return RSocket连接对象
     */
    public Object getRequester() {
        return requester;
    }

    /**
     * 设置底层RSocket连接
     *
     * @param requester RSocket连接对象
     */
    public void setRequester(Object requester) {
        this.requester = requester;
    }

    @Override
    public String toString() {
        return "RSocketSession{" +
                "id='" + id + '\'' +
                ", clientId='" + clientId + '\'' +
                ", connected=" + connected +
                ", connectTime=" + connectTime +
                '}';
    }
}
