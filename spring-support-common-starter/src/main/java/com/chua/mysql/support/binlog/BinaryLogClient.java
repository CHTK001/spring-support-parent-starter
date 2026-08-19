package com.chua.mysql.support.binlog;

import com.chua.mysql.support.binlog.event.Event;

/**
 * MySQL Binary Log 客户端
 *
 * @author CH
 */
public class BinaryLogClient {

    /** 主机 */
    private final String host;
    /** 端口 */
    private final int port;
    /** Username */
    private final String username;
    /** 密码 */
    private final String password;
    /** 服务器ID */
    private int serverId = 0;
    /** Connect超时 */
    private int connectTimeout = 0;

    /**
     * 创建 MySQL Binary Log 客户端
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
     * 设置服务器 ID
     *
     * @param serverId 服务器 ID
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
     * Binary Log 事件监听器接口
     */
    public interface EventListener {

        /**
         * 处理 Binary Log 事件
         *
         * @param event 事件对象
         */
        void onEvent(Event event);
    }
}
