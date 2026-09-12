package com.chua.socket.support.session;

import java.util.Map;

/**
* 套接字 会话接口
* 定义统一的会话操作方法
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
public interface SocketSession {

    /**
    * 获取会话标识
    *
    * @return 会话ID
     */
    String getId();

    /**
    * 获取客户端标识
    *
    * @return 客户端ID
     */
    String getClientId();

    /**
    * 设置客户端标识
    *
    * @param clientId 客户端标识
     */
    void setClientId(String clientId);

    /**
    * 获取用户信息
    *
    * @return 用户信息
     */
    SocketUser getUser();

    /**
    * 设置用户信息
    *
    * @param user 用户信息
     */
    void setUser(SocketUser user);

    /**
    * 设置属性
    *
    * @param key   属性键
    * @param value 属性值
     */
    void setAttribute(String key, Object value);

    /**
    * 获取属性
    *
    * @param key 属性键
    * @param <T> 属性类型
    * @return 属性值
     */
    <T> T getAttribute(String key);

    /**
    * 移除属性
    *
    * @param key 属性键
     */
    void removeAttribute(String key);

    /**
    * 获取所有属性
    *
    * @return 属性Map
     */
    Map<String, Object> getAttributes();

    /**
    * 是否已连接
    *
    * @return 是否已连接
     */
    boolean isConnected();

    /**
    * 断开连接
     */
    void disconnect();

    /**
    * 发送消息
    *
    * @param event 事件名称
    * @param data  消息数据
     */
    void send(String event, Object data);

    /**
    * 发送二进制数据
    *
    * @param event 事件名称
    * @param data  二进制数据
     */
    default void sendBinary(String event, byte[] data) {
 // 默认实现: 转换为 基础64 发送
        /**
        * 发送。
        * @param event 事件
        * @param java.util.Base64.getEncoder( Java.util.基础64.获取编码器(
         */
        send(event, java.util.Base64.getEncoder().encodeToString(data));
    }

    /**
    * 获取底层连接对象
    *
    * @param <T> 连接对象类型
    * @return 底层连接对象
     */
    <T> T getConnection();
}
