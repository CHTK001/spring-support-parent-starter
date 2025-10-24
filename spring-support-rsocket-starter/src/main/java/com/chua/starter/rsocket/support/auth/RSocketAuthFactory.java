package com.chua.starter.rsocket.support.auth;

import com.chua.starter.rsocket.support.session.RSocketSession;
import com.chua.starter.rsocket.support.session.RSocketUser;

import java.util.Map;

/**
 * RSocket认证工厂接口
 * <p>
 * 提供RSocket连接的认证和用户信息获取功能
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
public interface RSocketAuthFactory {

    /**
     * 认证连接请求
     * 
     * @param session     RSocket会话
     * @param credentials 认证凭证
     * @return 是否认证成功
     */
    boolean authenticate(RSocketSession session, Map<String, Object> credentials);

    /**
     * 获取用户信息
     * 
     * @param session RSocket会话
     * @return 用户信息，认证失败返回null
     */
    default RSocketUser getUser(RSocketSession session) {
        return session.getUser();
    }

    /**
     * 是否需要认证
     * 
     * @return 是否需要认证
     */
    default boolean isAuthRequired() {
        return true;
    }
}

