package com.chua.starter.sync.support;

/**
 * 同步服务端协议接口，定义协议无关的同步服务端操作。
 *
 * <p>与 {@link SyncClient} 成对使用，由 {@link SyncProtocol} 创建。</p>
 *
 * @author CH
 * @since 4.0.0.41
 */
public interface SyncServer {

    /**
     * 启动服务端。
     */
    void start();

    /**
     * 停止服务端。
     */
    void stop();

    /**
     * 判断服务端是否运行中。
     *
     * @return true 表示运行中
     */
    boolean isRunning();

    /**
     * 向指定会话发送消息。
     *
     * @param sessionId 会话标识
     * @param topic     消息主题
     * @param data      消息数据
     */
    void send(String sessionId, String topic, Object data);

    /**
     * 广播消息到所有已连接客户端。
     *
     * @param topic 消息主题
     * @param data  消息数据
     */
    void broadcast(String topic, Object data);

    /**
     * 广播消息到除指定会话外的其他客户端。
     *
     * @param excludeSessionId 排除的会话标识
     * @param topic            消息主题
     * @param data             消息数据
     */
    void broadcastToOthers(String excludeSessionId, String topic, Object data);

    /**
     * 添加连接事件监听器。
     *
     * @param listener 连接监听器
     */
    void addConnectionListener(SyncConnectionListener listener);

    /**
     * 添加消息监听器。
     *
     * @param listener 消息监听器
     */
    void addMessageListener(SyncMessageListener listener);

    /**
     * 获取指定会话。
     *
     * @param sessionId 会话标识
     * @return 会话对象，不存在返回 null
     */
    SyncSession getSession(String sessionId);

    /**
     * 获取所有会话标识。
     *
     * @return 会话标识集合
     */
    java.util.Set<String> getSessionIds();

    /**
     * 获取当前连接数。
     *
     * @return 连接数
     */
    int getConnectionCount();
}