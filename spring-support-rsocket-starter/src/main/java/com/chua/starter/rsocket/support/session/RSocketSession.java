package com.chua.starter.rsocket.support.session;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.json.Json;
import com.chua.starter.rsocket.support.auth.RSocketAuthFactory;
import com.chua.starter.rsocket.support.properties.RSocketProperties;
import io.rsocket.RSocket;
import io.rsocket.util.ByteBufPayload;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSocket会话
 * <p>
 * 封装RSocket客户端连接信息和操作方法
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Slf4j
public class RSocketSession {

    /**
     * RSocket客户端实例
     */
    private final RSocket rsocket;

    /**
     * 会话ID
     */
    @Getter
    private final String sessionId;

    /**
     * RSocket配置属性
     */
    private final RSocketProperties rsocketProperties;

    /**
     * 认证工厂
     */
    private final RSocketAuthFactory authFactory;

    /**
     * 会话属性
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param sessionId        会话ID
     * @param rsocket          RSocket客户端实例
     * @param rsocketProperties RSocket配置属性
     * @param authFactory      认证工厂
     */
    public RSocketSession(String sessionId, RSocket rsocket, RSocketProperties rsocketProperties, RSocketAuthFactory authFactory) {
        this.sessionId = sessionId;
        this.rsocket = rsocket;
        this.rsocketProperties = rsocketProperties;
        this.authFactory = authFactory;
    }

    /**
     * 判断是否匹配指定的会话ID
     * 
     * @param sessionId 会话ID
     * @return 是否匹配
     */
    public boolean isMatch(String sessionId) {
        return this.sessionId.equals(sessionId);
    }

    /**
     * 关闭会话
     */
    public void close() {
        try {
            if (rsocket != null && !rsocket.isDisposed()) {
                rsocket.dispose();
            }
        } catch (Exception e) {
            log.error("关闭RSocket会话失败: {}", sessionId, e);
        }
    }

    /**
     * 发送消息
     * 
     * @param event 事件名称
     * @param msg   消息内容
     */
    public void send(String event, String msg) {
        send(event, ReturnResult.success(msg));
    }

    /**
     * 发送消息
     * 
     * @param event        事件名称
     * @param returnResult 返回结果
     */
    public void send(String event, ReturnResult returnResult) {
        try {
            if (rsocket == null || rsocket.isDisposed()) {
                log.warn("RSocket客户端未连接或已关闭，无法发送消息: {}", sessionId);
                return;
            }

            String jsonData = Json.toJSONString(returnResult);
            rsocket.fireAndForget(
                    ByteBufPayload.create(
                            jsonData.getBytes(StandardCharsets.UTF_8)
                    )
            ).subscribe();

            log.debug("发送消息到会话 {}: event={}, data={}", sessionId, event, jsonData);
        } catch (Exception e) {
            log.error("发送消息失败: sessionId={}, event={}", sessionId, event, e);
        }
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    public RSocketUser getUser() {
        return (RSocketUser) attributes.get("user");
    }

    /**
     * 设置用户信息
     * 
     * @param user 用户信息
     */
    public void setUser(RSocketUser user) {
        attributes.put("user", user);
    }

    /**
     * 获取属性
     * 
     * @param key 属性键
     * @return 属性值
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 设置属性
     * 
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
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
     * 判断会话是否有效
     * 
     * @param sessionId 会话ID
     * @return 是否有效
     */
    public boolean isValid(String sessionId) {
        return this.sessionId.equals(sessionId) && rsocket != null && !rsocket.isDisposed();
    }

    /**
     * 获取RSocket客户端实例
     * 
     * @return RSocket客户端实例
     */
    public RSocket getRSocket() {
        return rsocket;
    }
}

