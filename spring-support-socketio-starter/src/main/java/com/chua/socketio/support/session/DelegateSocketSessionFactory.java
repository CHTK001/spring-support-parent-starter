package com.chua.socketio.support.session;

import com.chua.socketio.support.properties.SocketIoProperties;
import com.corundumstudio.socketio.SocketIOClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话
 * @author CH
 */
public class DelegateSocketSessionFactory implements SocketSessionTemplate {

    private final Map<String, SocketSession> cache = new ConcurrentHashMap<>();
    private final SocketIoProperties socketIoProperties;

    public DelegateSocketSessionFactory(SocketIoProperties socketIoProperties) {
        this.socketIoProperties = socketIoProperties;
    }

    @Override
    public SocketSession save(SocketIOClient client) {
        SocketSession socketSession = new SocketSession(client, socketIoProperties);
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

    @Override
    public void send(String event, String msg) {
        for (SocketSession socketSession : cache.values()) {
            socketSession.send(event, msg);
        }
    }
}
