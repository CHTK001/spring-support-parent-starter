package com.chua.socketio.support.session;

import com.corundumstudio.socketio.SocketIOClient;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话
 *
 * @author CH
 */
@Slf4j
public class SocketSession {

    private final SocketIOClient client;
    private final String sessionId;

    public SocketSession(SocketIOClient client) {
        this.client = client;
        this.sessionId = client.getSessionId().toString();
    }

    /**
     * 是否匹配
     *
     * @param client 客戶端
     * @return 結果
     */
    public boolean isMatch(SocketIOClient client) {
        return sessionId.equals(client.getSessionId().toString());
    }

    /**
     * 是否匹配
     *
     * @param sessionId sessionId
     * @return 結果
     */
    public boolean isMatch(String sessionId) {
        return this.sessionId.equals(sessionId);
    }

    /**
     * 關閉
     */
    public void close() {
        try {
            client.disconnect();
        } catch (Exception e) {
            log.error("", e);
        }
    }

    /**
     * 下发命令
     *
     * @param event 时间
     * @param msg   消息
     */
    public void send(String event, String msg) {
        client.sendEvent(event, msg);
    }

}
