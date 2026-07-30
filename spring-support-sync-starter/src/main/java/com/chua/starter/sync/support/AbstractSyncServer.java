package com.chua.starter.sync.support;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象同步服务端，封装会话管理和监听器分发。
 *
 * <p>子类只需实现 {@link #doStart()}、{@link #doStop()}、{@link #doSend(String, String, Object)}。</p>
 *
 * @author CH
 * @since 4.0.0.41
 */
@Slf4j
public abstract class AbstractSyncServer implements SyncServer {

    /**
     * 运行状态
     */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 会话映射：sessionId -> SyncSession
     */
    protected final Map<String, SyncSession> sessions = new ConcurrentHashMap<>();

    /**
     * 连接监听器列表
     */
    protected final java.util.List<SyncConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    /**
     * 消息监听器列表
     */
    protected final java.util.List<SyncMessageListener> messageListeners = new CopyOnWriteArrayList<>();

    /**
     * 协议类型
     */
    protected String protocol;

    /**
     * 绑定地址
     */
    protected String host;

    /**
     * 绑定端口
     */
    protected int port;

    @Override
    public void start() {
        if (running.get()) {
            log.warn("[SyncServer] {} 已在运行中", protocol);
            return;
        }
        running.set(true);
        doStart();
        log.info("[SyncServer] {} 启动成功: {}:{}", protocol, host, port);
    }

    /**
     * 子类实现具体的启动逻辑。
     */
    protected abstract void doStart();

    @Override
    public void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        doStop();
        sessions.clear();
        log.info("[SyncServer] {} 已停止: {}:{}", protocol, host, port);
    }

    /**
     * 子类实现具体的停止逻辑。
     */
    protected abstract void doStop();

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void send(String sessionId, String topic, Object data) {
        SyncSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("[SyncServer] 会话不存在: {}", sessionId);
            return;
        }
        doSend(sessionId, topic, data);
    }

    /**
     * 子类实现具体的消息发送逻辑。
     *
     * @param sessionId 会话标识
     * @param topic     消息主题
     * @param data      消息数据
     */
    protected abstract void doSend(String sessionId, String topic, Object data);

    @Override
    public void broadcast(String topic, Object data) {
        for (String sessionId : sessions.keySet()) {
            doSend(sessionId, topic, data);
        }
    }

    @Override
    public void broadcastToOthers(String excludeSessionId, String topic, Object data) {
        for (Map.Entry<String, SyncSession> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(excludeSessionId)) {
                doSend(entry.getKey(), topic, data);
            }
        }
    }

    @Override
    public void addConnectionListener(SyncConnectionListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
        }
    }

    @Override
    public void addMessageListener(SyncMessageListener listener) {
        if (listener != null) {
            messageListeners.add(listener);
        }
    }

    @Override
    public SyncSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public Set<String> getSessionIds() {
        return sessions.keySet();
    }

    @Override
    public int getConnectionCount() {
        return sessions.size();
    }

    /**
     * 分发连接建立事件。
     *
     * @param session 连接会话
     */
    protected void fireConnectionEstablished(SyncSession session) {
        sessions.put(session.getSessionId(), session);
        for (SyncConnectionListener listener : connectionListeners) {
            try {
                listener.onConnect(session);
            } catch (Exception e) {
                log.error("[SyncServer] 连接监听器执行失败", e);
            }
        }
    }

    /**
     * 分发连接断开事件。
     *
     * @param session 连接会话
     */
    protected void fireConnectionClosed(SyncSession session) {
        sessions.remove(session.getSessionId());
        for (SyncConnectionListener listener : connectionListeners) {
            try {
                listener.onDisconnect(session);
            } catch (Exception e) {
                log.error("[SyncServer] 断开监听器执行失败", e);
            }
        }
    }

    /**
     * 分发消息接收事件。
     *
     * @param session 发送消息的会话
     * @param topic   消息主题
     * @param data    消息数据
     */
    protected void fireMessageReceived(SyncSession session, String topic, Object data) {
        SyncMessage message = new SyncMessage(topic, data);
        for (SyncMessageListener listener : messageListeners) {
            try {
                listener.onMessage(session, message);
            } catch (Exception e) {
                log.error("[SyncServer] 消息监听器执行失败", e);
            }
        }
    }
}