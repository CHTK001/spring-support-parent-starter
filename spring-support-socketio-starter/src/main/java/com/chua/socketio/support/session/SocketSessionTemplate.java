package com.chua.socketio.support.session;

import com.corundumstudio.socketio.SocketIOClient;

/**
 * 会话
 *
 * @author CH
 */
public interface SocketSessionTemplate {
    /**
     * 注册会话
     *
     * @param client 客户端
     */
    SocketSession save(SocketIOClient client);

    /**
     * 刪除繪畫
     *
     * @param client 客戶端
     */
    void remove(SocketIOClient client);

    /**
     * 获取session
     * @param sessionId session
     * @return 结果
     */
    SocketSession getSession(String sessionId);
}
