package com.chua.socketio.support.resolver;

import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.corundumstudio.socketio.SocketIOClient;

/**
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
public interface SocketSessionResolver {
    /**
     * 连接
     *
     * @param client 客户端
     */
    void doConnect(SocketIOClient client);

    /**
     * 失去连接
     *
     * @param client 客户端
     */
    void disConnect(SocketIOClient client);

    /**
     * 注册监听
     * @param socketIOServer 服务端
     */
    void registerEvent(DelegateSocketIOServer socketIOServer);
}
