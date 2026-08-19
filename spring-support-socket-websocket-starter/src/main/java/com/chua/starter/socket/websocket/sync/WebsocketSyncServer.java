package com.chua.starter.socket.websocket.sync;

import com.chua.starter.sync.support.AbstractSyncServer;
import com.chua.starter.sync.support.SyncSession;
import com.chua.starter.socket.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 同步服务端实现。
 *
 * <p>基于 ${@code spring-boot-starter-websocket} 的 {@link WebSocketSessionManager} 实现同步协议。</p>
 *
 * @since 4.0.0.41
 * @author CH
 */
@Slf4j
public class WebsocketSyncServer extends AbstractSyncServer {

    /**
     * 会话管理器
     */
    private final WebSocketSessionManager sessionManager;

    /**
     * 构造 WebSocket 同步服务端。
     *
     * @param sessionManager 会话管理器
     */
    public WebsocketSyncServer(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.protocol = "websocket";
    }

    @Override
    /** Do开始 */
    protected void doStart() {
        for (WebSocketSession session : sessionManager.getAllSessions()) {
            onWebSocketConnect(session);
        }
    }

    @Override
    /** Do停止 */
    protected void doStop() {
    }

    @Override
    /** Do发送 */
    protected void doSend(String sessionId, String topic, Object data) {
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        String payload = data instanceof String ? (String) data : String.valueOf(data);
        try {
            synchronized (session) {
                session.sendMessage(new org.springframework.web.socket.TextMessage(payload));
            }
        } catch (Exception e) {
            log.error("[WebsocketSyncServer] 发送消息失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * WebSocket 连接建立时回调。
     *
     * @param session Spring WebSocket 会话
     */
    public void onWebSocketConnect(WebSocketSession session) {
        SyncSession syncSession = new SyncSession(session.getId());
        syncSession.setClientIp(session.getRemoteAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress() : null);
        syncSession.setClientPort(session.getRemoteAddress() != null
                ? session.getRemoteAddress().getPort() : 0);
        fireConnectionEstablished(syncSession);
    }

    /**
     * WebSocket 连接断开时回调。
     *
     * @param session Spring WebSocket 会话
     */
    public void onWebSocketDisconnect(WebSocketSession session) {
        SyncSession syncSession = sessions.get(session.getId());
        if (syncSession != null) {
            fireConnectionClosed(syncSession);
        }
    }

    /**
     * WebSocket 消息到达时回调。
     *
     * @param session Spring WebSocket 会话
     * @param message 消息内容
     */
    public void onWebSocketMessage(WebSocketSession session, String message) {
        SyncSession syncSession = sessions.get(session.getId());
        if (syncSession == null) {
            syncSession = new SyncSession(session.getId());
            sessions.put(session.getId(), syncSession);
        }
        fireMessageReceived(syncSession, "sync/message", message);
    }
}