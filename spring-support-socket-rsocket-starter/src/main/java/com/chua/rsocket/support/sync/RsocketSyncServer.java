package com.chua.rsocket.support.sync;

import com.chua.rsocket.support.session.RSocketSessionTemplateImpl;
import com.chua.starter.sync.support.AbstractSyncServer;
import com.chua.starter.sync.support.SyncSession;
import lombok.extern.slf4j.Slf4j;

/**
 * rSocket 同步服务端实现。
 *
 * @since 4.0.0.41
 * @author CH
 */
@Slf4j
public class RsocketSyncServer extends AbstractSyncServer {

    /**
     * rSocket 会话模板
     */
    private final RSocketSessionTemplateImpl sessionTemplate;

    /**
     * 构造 rSocket 同步服务端。
     *
     * @param sessionTemplate rSocket 会话模板
     */
    public RsocketSyncServer(RSocketSessionTemplateImpl sessionTemplate) {
        this.sessionTemplate = sessionTemplate;
        this.protocol = "rsocket";
    }

    @Override
    /** 执行开始 */
    protected void doStart() {
    }

    @Override
    /** 执行停止 */
    protected void doStop() {
    }

    @Override
    /** 执行发送 */
    protected void doSend(String sessionId, String topic, Object data) {
        String payload = data instanceof String ? (String) data : String.valueOf(data);
        sessionTemplate.send(sessionId, topic, payload);
    }

    /**
     * rSocket 连接建立时回调。
     *
     * @param sessionId 会话标识
     */
    public void onRsocketConnect(String sessionId) {
        SyncSession syncSession = new SyncSession(sessionId);
        /**
         * fireconnectionestablished。
         * @param syncSession 同步会话
         */
        fireConnectionEstablished(syncSession);
    }

    /**
     * rSocket 连接断开时回调。
     *
     * @param sessionId 会话标识
     */
    public void onRsocketDisconnect(String sessionId) {
        SyncSession syncSession = sessions.get(sessionId);
        if (syncSession != null) {
            /**
             * fireconnection关闭。
             * @param syncSession 同步会话
             */
            fireConnectionClosed(syncSession);
        }
    }

    /**
     * rSocket 消息到达时回调。
     *
     * @param sessionId 会话标识
     * @param topic     消息主题
     * @param data      消息数据
     */
    public void onRsocketMessage(String sessionId, String topic, Object data) {
        SyncSession syncSession = sessions.get(sessionId);
        if (syncSession == null) {
            syncSession = new SyncSession(sessionId);
            sessions.put(sessionId, syncSession);
        }
        /**
         * fire消息接收。
         * @param syncSession 同步会话
         * @param topic topic
         * @param data 数据
         */
        fireMessageReceived(syncSession, topic, data);
    }
}
