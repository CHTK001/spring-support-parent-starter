package com.chua.starter.rsocket.support.resolver;

import com.chua.starter.rsocket.support.session.RSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认RSocket会话解析器实现
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Slf4j
@Component
public class DefaultRSocketSessionResolver implements RSocketSessionResolver {

    /**
     * 会话存储
     */
    private final Map<String, RSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void addSession(RSocketSession session) {
        sessions.put(session.getSessionId(), session);
        log.debug("添加RSocket会话: sessionId={}, 当前会话数={}", 
                session.getSessionId(), sessions.size());
    }

    @Override
    public void removeSession(String sessionId) {
        RSocketSession session = sessions.remove(sessionId);
        if (session != null) {
            log.debug("移除RSocket会话: sessionId={}, 剩余会话数={}", 
                    sessionId, sessions.size());
        }
    }

    @Override
    public RSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    @Override
    public int getSessionCount() {
        return sessions.size();
    }
}

