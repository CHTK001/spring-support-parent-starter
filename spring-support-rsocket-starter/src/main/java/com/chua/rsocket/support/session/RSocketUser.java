package com.chua.rsocket.support.session;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSocket 用户信息
 * 封装连接用户的基本信息
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
public class RSocketUser implements Serializable {

    private static final long serialVersionUID = 1L;

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

    public RSocketUser() {
    }

    public RSocketUser(String userId) {
        this.userId = userId;
    }

    public RSocketUser(String userId, String username) {
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
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key) {
        return (T) extras.get(key);
    }
}
