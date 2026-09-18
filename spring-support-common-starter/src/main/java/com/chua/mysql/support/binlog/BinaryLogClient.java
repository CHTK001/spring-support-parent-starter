package com.chua.mysql.support.binlog;

import com.chua.mysql.support.binlog.event.Event;

/**
* MySQL Binary 日志 客户端
*
* @author CH
* @since 4.0.0
 */
public class BinaryLogClient {

    /** 主机 */
    private final String host;
    /** 端口 */
    private final int port;
    /** 用户名 */
    private final String username;
    /** 密码 */
    private final String password;
    /** 服务器标识 */
    private int serverId = 0;
    /** 连接超时 */
    private int connectTimeout = 0;

    /**
    * 创建 MySQL Binary 日志 客户端
    *
    * @param host 主机地址
    * @param port 端口
    * @param username 用户名
    * @param password 密码
    */
    public BinaryLogClient(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
    * 设置服务器 标识
    *
    * @param serverId 服务器 标识
    */
    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    /**
    * 设置连接超时时间
    *
    * @param timeout 超时时间（毫秒）
    */
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    /**
    * 注册事件监听器
    *
    * @param eventListener 事件监听器
    */
    public void registerEventListener(EventListener eventListener) {
    }

    /**
    * 取消注册事件监听器
    *
    * @param eventListener 事件监听器
    */
    public void unregisterEventListener(EventListener eventListener) {
    }

    /**
    * 连接到 MySQL 服务器
    */
    public void connect() {
    }

    /**
    * 断开连接
    */
    public void disconnect() {
    }

    /**
    * Binary 日志 事件监听器接口
    * @author CH
    * @since 4.0.0
    */
    public interface EventListener {

        /**
        * 处理 Binary 日志 事件
        *
        * @param event 事件对象
        */
        void onEvent(Event event);
    }
}
