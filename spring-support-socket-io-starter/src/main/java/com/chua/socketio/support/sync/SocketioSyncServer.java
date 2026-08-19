package com.chua.socketio.support.sync;

import com.chua.socketio.support.session.SocketIOSessionTemplate;
import com.chua.starter.sync.support.AbstractSyncServer;
import com.chua.starter.sync.support.SyncSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Socket.IO 同步服务端实现。
 *
 * @since 4.0.0.41
 * @author CH
 */
@Slf4j
public class SocketioSyncServer extends AbstractSyncServer {

    /**
     * Socket.IO 会话模板
     */
    private final SocketIOSessionTemplate sessionTemplate;

    /**
     * 构造 Socket.IO 同步服务端。
     *
     * @param sessionTemplate Socket.IO 会话模板
     */
    public SocketioSyncServer(SocketIOSessionTemplate sessionTemplate) {
        this.sessionTemplate = sessionTemplate;
        this.protocol = "socketio";
    }

    @Override
    protected void doStart() {
    }

    @Override
    protected void doStop() {
    }

    @Override
    protected void doSend(String sessionId, String topic, Object data) {
        String payload = data instanceof String ? (String) data : String.valueOf(data);
        sessionTemplate.send(sessionId, topic, payload);
    }

    /**
     * Socket.IO 连接建立时回调。
     *
     * @param sessionId 会话标识
     */
    public void onSocketioConnect(String sessionId) {
        SyncSession syncSession = new SyncSession(sessionId);
        fireConnectionEstablished(syncSession);
    }

    /**
     * Socket.IO 连接断开时回调。
     *
     * @param sessionId 会话标识
     */
    public void onSocketioDisconnect(String sessionId) {
        SyncSession syncSession = sessions.get(sessionId);
        if (syncSession != null) {
            fireConnectionClosed(syncSession);
        }
    }

    /**
     * Socket.IO 消息到达时回调。
     *
     * @param sessionId 会话标识
     * @param topic     消息主题
     * @param data      消息数据
     */
    public void onSocketioMessage(String sessionId, String topic, Object data) {
        SyncSession syncSession = sessions.get(sessionId);
        if (syncSession == null) {
            syncSession = new SyncSession(sessionId);
            sessions.put(sessionId, syncSession);
        }
        fireMessageReceived(syncSession, topic, data);
    }
}