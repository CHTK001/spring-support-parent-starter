package com.chua.starter.rsocket.support.auth;

import com.chua.starter.rsocket.support.session.RSocketSession;
import com.chua.starter.rsocket.support.session.RSocketUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认RSocket认证工厂实现
 * <p>
 * 提供默认的认证逻辑（允许所有连接）
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Slf4j
@Component
public class DefaultRSocketAuthFactory implements RSocketAuthFactory {

    @Override
    public boolean authenticate(RSocketSession session, Map<String, Object> credentials) {
        // 默认实现：允许所有连接
        log.debug("使用默认认证工厂，允许所有连接: sessionId={}", session.getSessionId());
        
        // 创建默认用户
        RSocketUser user = new RSocketUser();
        user.setUserId(session.getSessionId());
        user.setUsername("guest");
        session.setUser(user);
        
        return true;
    }

    @Override
    public boolean isAuthRequired() {
        return false;
    }
}

