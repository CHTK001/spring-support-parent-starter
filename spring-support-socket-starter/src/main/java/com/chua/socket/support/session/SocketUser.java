package com.chua.socket.support.session;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Socket 用户信息
 * 封装连接用户的基本信息
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
public class SocketUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * room
     */
    private String roomId;
    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户类型
     */
    private String type;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 额外属性
     */
    private Map<String, Object> extras = new ConcurrentHashMap<>();

    public SocketUser() {
    }

    public SocketUser(String userId) {
        this.userId = userId;
    }

    public SocketUser(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    /**
     * 设置额外属性
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setExtra(String key, Object value) {
        extras.put(key, value);
    }

    /**
     * 获取额外属性
     *
     * @param key 属性键
     * @param <T> 属性类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key) {
        return (T) extras.get(key);
    }
}
