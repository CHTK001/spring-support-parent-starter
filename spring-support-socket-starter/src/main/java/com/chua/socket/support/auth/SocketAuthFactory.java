package com.chua.socket.support.auth;

import com.chua.socket.support.session.SocketUser;

import java.util.Map;

/**
 * Socket 认证工厂接口
 * 用于验证客户端连接的合法性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public interface SocketAuthFactory {

    /**
     * 验证是否授权
     *
     * @param handshakeData 握手数据
     * @return 是否授权
     */
    boolean isAuthorized(Map<String, Object> handshakeData);

    /**
     * 获取用户信息
     *
     * @param handshakeData 握手数据
     * @return 用户信息
     */
    SocketUser getUser(Map<String, Object> handshakeData);
}
