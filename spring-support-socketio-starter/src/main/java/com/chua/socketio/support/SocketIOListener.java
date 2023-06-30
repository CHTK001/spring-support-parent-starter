package com.chua.socketio.support;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;

/**
 * 监听
 *
 * @author CH
 */
public interface SocketIOListener {
    /**
     * 建立连接
     *
     * @param client 客户端
     */
    @OnConnect
    void onConnect(SocketIOClient client);
    /**
     * 建立连接
     *
     * @param client 客户端
     */
    @OnDisconnect
    void onDisConnect(SocketIOClient client);
}
