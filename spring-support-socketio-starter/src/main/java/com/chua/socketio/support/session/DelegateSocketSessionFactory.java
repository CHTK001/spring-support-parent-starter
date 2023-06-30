package com.chua.socketio.support.session;

import com.corundumstudio.socketio.SocketIOClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话
 * @author CH
 */
public class DelegateSocketSessionFactory implements SocketSessionTemplate {

    private final List<SocketSession> cache = new CopyOnWriteArrayList<>();
    @Override
    public SocketSession save(SocketIOClient client) {
        SocketSession socketSession = new SocketSession(client);
        cache.add(socketSession);
        return socketSession;
    }

    @Override
    public void remove(SocketIOClient client) {
        List<SocketSession> less = new ArrayList<>();
        for (SocketSession socketSession : cache) {
            if(socketSession.isMatch(client)) {
                less.add(socketSession);
                socketSession.close();
                break;
            }
        }
        cache.removeAll(less);
        return;
    }

    @Override
    public SocketSession getSession(String sessionId) {
        return cache.stream().filter(it -> it.isMatch(sessionId)).findFirst().get();
    }
}
