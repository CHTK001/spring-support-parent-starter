package com.chua.socketio.support.session;

import com.corundumstudio.socketio.SocketIOClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话
 * @author CH
 */
public class DelegateSocketSessionFactory implements SocketSessionTemplate {

    private final Map<String, SocketSession> cache = new ConcurrentHashMap<>();
    @Override
    public SocketSession save(SocketIOClient client) {
        SocketSession socketSession = new SocketSession(client);
        cache.put(client.getSessionId().toString(), socketSession);
        return socketSession;
    }

    @Override
    public void remove(SocketIOClient client) {
        cache.remove(client.getSessionId().toString());
    }

    @Override
    public SocketSession getSession(String sessionId) {
        return cache.get(sessionId);
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        SocketSession session = getSession(sessionId);
        session.send(event, msg);
    }
}
