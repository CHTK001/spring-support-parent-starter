package com.chua.socketio.support.auth;

import com.corundumstudio.socketio.HandshakeData;

/**
 * 套接字验证工厂
 *
 * @author CH
 */
public interface SocketAuthFactory {
    /**
     * 被授权
     *
     * @param data 数据
     * @return boolean
     */
    boolean isAuthorized(HandshakeData data);
}
