package com.chua.socketio.support.session;

import com.chua.socketio.support.properties.SocketIoProperties;
import com.corundumstudio.socketio.SocketIOClient;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话
 * @author CH
 */
public class DelegateSocketSessionFactory implements SocketSessionTemplate {

    private final Map<String, SocketSession> cache = new ConcurrentHashMap<>();
    private final SocketIoProperties socketIoProperties;
    private final Map<String, List<String>> clientIdAndSessionId = new ConcurrentHashMap<>();

    public DelegateSocketSessionFactory(SocketIoProperties socketIoProperties) {
        this.socketIoProperties = socketIoProperties;
    }

    @Override
    public SocketSession save(String clientId, SocketIOClient client) {
        SocketSession socketSession = new SocketSession(client, socketIoProperties);
        clientIdAndSessionId.computeIfAbsent(clientId, it -> new LinkedList<>()).add(client.getSessionId().toString());
        cache.put(client.getSessionId().toString(), socketSession);
        return socketSession;
    }

    @Override
    public void remove(String clientId, SocketIOClient client) {
        List<String> strings = clientIdAndSessionId.get(clientId);
        if(null != strings) {
            strings.remove(client.getSessionId().toString());
        }
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

    @Override
    public void sendClient(String clientId, String event, String msg) {
        List<String> strings = clientIdAndSessionId.get(clientId);
        if(null == strings) {
            return;
        }

        for (String string : strings) {
            SocketSession socketSession = cache.get(string);
            if(null == socketSession) {
                continue;
            }
            socketSession.send(event, msg);
        }
    }
}
