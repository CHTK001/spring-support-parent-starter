package com.chua.starter.sync.support;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.network.server.AbstractServer;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 同步服务端抽象基类，封装会话管理与事件分发通用逻辑。
 * <p>
   * 各协议（web套接字、套接字.IO、r套接字 等）的同步服务端实现继承该类，只需实现
 * {@link #doStart()}、{@link #doStop()}、{@link #doSend(String, String, Object)} 三个协议相关方法，
 * 并在合适时机调用 {@link #fireConnectionEstablished(SyncSession)}、
 * {@link #fireConnectionClosed(SyncSession)}、{@link #fireMessageReceived(SyncSession, String, Object)}
 * 触发同步事件。
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public abstract class AbstractSyncServer extends AbstractServer implements SyncServer {

    /**
       * 协议名称（小写，如 WebSocket、套接字.IO、rsocket），由子类构造函数设置。
     */
    protected String protocol;

    /**
       * 当前活跃会话（会话标识 -> 同步会话）。
     */
    protected final Map<String, SyncSession> sessions = new ConcurrentHashMap<>();

    /**
     * 同步事件监听器列表。
     */
    protected final List<SyncServerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 构造函数，使用默认服务器配置。
     */
    protected AbstractSyncServer() {
        /**
         * 父。
         * @param ServerSetting.defaults( 服务端setting.默认(
         */
        super(ServerSetting.defaults());
    }

    @Override
    /** 获取协议类型 */
    public ProtocolType getProtocolType() {
        if (protocol == null) {
            return ProtocolType.UNKNOWN;
        }
        return switch (protocol) {
            case "websocket" -> ProtocolType.WS;
            case "socketio" -> ProtocolType.SOCKET_IO;
            case "rsocket" -> ProtocolType.RSOCKET;
            default -> ProtocolType.UNKNOWN;
        };
    }

    @Override
    /** 发布 */
    public void publish(String topic, Object message) {
        for (String sessionId : sessions.keySet()) {
            /**
             * 执行发送。
             * @param sessionId 会话标识
             * @param topic topic
             * @param message 消息
             */
            doSend(sessionId, topic, message);
        }
    }

    @Override
    /** 发送 */
    public void send(String clientId, String topic, Object message) {
        /**
         * 执行发送。
         * @param clientId 客户端标识
         * @param topic topic
         * @param message 消息
         */
        doSend(clientId, topic, message);
    }

    @Override
    /** 获取连接客户端 */
    public List<String> getConnectedClients() {
        return List.copyOf(sessions.keySet());
    }

    @Override
    /** 获取客户端metadata */
    public Map<String, Object> getClientMetadata(String clientId) {
        SyncSession session = sessions.get(clientId);
        return session == null ? Map.of() : session.toMetadata();
    }

    @Override
    /** 添加监听器 */
    public void addListener(SyncServerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    /** 移除监听器 */
    public void removeListener(SyncServerListener listener) {
        listeners.remove(listener);
    }

    /**
     * 向指定会话发送消息，由子类实现具体协议的发送逻辑。
     *
     * @param sessionId 会话标识
     * @param topic     消息主题
     * @param data      消息数据
     */
    protected abstract void doSend(String sessionId, String topic, Object data);

    /**
     * 连接建立时触发，记录会话并通知监听器。
     *
     * @param session 同步会话
     */
    protected void fireConnectionEstablished(SyncSession session) {
        sessions.put(session.getId(), session);
        for (SyncServerListener listener : listeners) {
            listener.onClientConnected(session.getId(), session.toMetadata());
        }
    }

    /**
     * 连接断开时触发，移除会话并通知监听器。
     *
     * @param session 同步会话
     */
    protected void fireConnectionClosed(SyncSession session) {
        sessions.remove(session.getId());
        for (SyncServerListener listener : listeners) {
            listener.onClientDisconnected(session.getId());
        }
    }

    /**
     * 收到消息时触发，通知监听器。
     *
     * @param session 同步会话
     * @param topic   消息主题
     * @param data    消息数据
     */
    protected void fireMessageReceived(SyncSession session, String topic, Object data) {
        for (SyncServerListener listener : listeners) {
            listener.onMessage(session.getId(), topic, data);
        }
    }
}
