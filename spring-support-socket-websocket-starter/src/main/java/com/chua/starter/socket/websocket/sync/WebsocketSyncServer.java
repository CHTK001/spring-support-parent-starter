package com.chua.starter.socket.websocket.sync;

import com.chua.starter.sync.support.AbstractSyncServer;
import com.chua.starter.sync.support.SyncSession;
import com.chua.starter.socket.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

/**
 * webSocket 同步服务端实现。
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
     * 构造 webSocket 同步服务端。
     *
     * @param sessionManager 会话管理器
     */
    public WebsocketSyncServer(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.protocol = "websocket";
    }

    @Override
    /** 执行开始 */
    protected void doStart() {
        for (WebSocketSession session : sessionManager.getAllSessions()) {
            /**
             * onwebSocket连接。
             * @param session 会话
             */
            onWebSocketConnect(session);
        }
    }

    @Override
    /** 执行停止 */
    protected void doStop() {
    }

    @Override
    /** 执行发送 */
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
     * webSocket 连接建立时回调。
     *
     * @param session Spring webSocket 会话
     */
    public void onWebSocketConnect(WebSocketSession session) {
        SyncSession syncSession = new SyncSession(session.getId());
        syncSession.setClientIp(session.getRemoteAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress() : null);
        syncSession.setClientPort(session.getRemoteAddress() != null
                ? session.getRemoteAddress().getPort() : 0);
        /**
         * fireconnectionestablished。
         * @param syncSession 同步会话
         */
        fireConnectionEstablished(syncSession);
    }

    /**
     * webSocket 连接断开时回调。
     *
     * @param session Spring webSocket 会话
     */
    public void onWebSocketDisconnect(WebSocketSession session) {
        SyncSession syncSession = sessions.get(session.getId());
        if (syncSession != null) {
            /**
             * fireconnection关闭。
             * @param syncSession 同步会话
             */
            fireConnectionClosed(syncSession);
        }
    }

    /**
     * webSocket 消息到达时回调。
     *
     * @param session Spring webSocket 会话
     * @param message 消息内容
     */
    public void onWebSocketMessage(WebSocketSession session, String message) {
        SyncSession syncSession = sessions.get(session.getId());
        if (syncSession == null) {
            syncSession = new SyncSession(session.getId());
            sessions.put(session.getId(), syncSession);
        }
        /**
         * fire消息接收。
         * @param syncSession 同步会话
         * @param "sync/message" "同步/消息"
         * @param message 消息
         */
        fireMessageReceived(syncSession, "sync/message", message);
    }
}
