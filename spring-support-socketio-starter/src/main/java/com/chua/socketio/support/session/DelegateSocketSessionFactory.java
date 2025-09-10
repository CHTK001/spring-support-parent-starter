package com.chua.socketio.support.session;

import com.chua.socketio.support.properties.SocketIoProperties;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.wrapper.WrapperConfiguration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话
 * @author CH
 */
public class DelegateSocketSessionFactory implements SocketSessionTemplate {

    private final Map<String, List<SocketSession>> cache = new ConcurrentHashMap<>();
    private final List<SocketInfo> socketInfos = new LinkedList<>();
    private final SocketIoProperties socketIoProperties;

    public DelegateSocketSessionFactory(SocketIoProperties socketIoProperties) {
        this.socketIoProperties = socketIoProperties;
    }

    @Override
    public SocketSession save(String clientId, SocketIOClient client) {
        SocketSession socketSession = new SocketSession(client, socketIoProperties);
        cache.computeIfAbsent(clientId, it->new CopyOnWriteArrayList<>()).add(socketSession);
        return socketSession;
    }

    @Override
    public void remove(String clientId, SocketIOClient client) {
        cache.remove(client.getSessionId().toString());
    }

    @Override
    public SocketSession getSession(String sessionId) {
        for (List<SocketSession> value : cache.values()) {
            for (SocketSession socketSession : value) {
                if(socketSession.isValid(sessionId)) {
                    return socketSession;
                }
            }
        }
        return null;
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        SocketSession session = getSession(sessionId);
        session.send(event, msg);
    }

    @Override
    public void send(String event, String msg) {
        for (List<SocketSession> list : cache.values()) {
            for (SocketSession socketSession : list) {
                socketSession.send(event, msg);
            }
        }
    }

    @Override
    public void sendClient(String clientId, String event, String msg) {
        List<SocketSession> socketSessions = cache.get(clientId);
        if(null == socketSessions) {
            return;
        }
        for (SocketSession socketSession : socketSessions) {
            socketSession.send(event, msg);
        }

    }

    @Override
    public List<SocketUser> getOnlineSession(String type) {
        List<SocketSession> socketSessions = cache.get(type);
        if(null == socketSessions) {
            return Collections.emptyList();
        }

        List<String> uid = new LinkedList<>();
        List<SocketUser> rs = new LinkedList<>();
        for (SocketSession socketSession : socketSessions) {
            SocketUser sessionUser = socketSession.getUser();
            if(uid.contains(sessionUser.getUid())) {
                continue;
            }
            rs.add(sessionUser);
            uid.add(sessionUser.getUid());
        }
        return rs;
    }

    @Override
    public SocketSession getOnlineSession(String type, String roomId, String target) {
        List<SocketSession> socketSessions = cache.get(type);
        if(null == socketSessions) {
            return null;
        }

        for (SocketSession socketSession : socketSessions) {
            SocketUser sessionUser = socketSession.getUser();
            if(target.contains(sessionUser.getUid()) && roomId.contains(sessionUser.getRoomId())) {
                return  socketSession;
            }
        }

        return null;
    }

    @Override
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        List<SocketSession> socketSessions = cache.get(type);
        if(null == socketSessions) {
            return null;
        }

        List<SocketSession> rs = new LinkedList<>();
        for (SocketSession socketSession : socketSessions) {
            SocketUser sessionUser = socketSession.getUser();
            if( roomId.equals(sessionUser.getRoomId())) {
                rs.add(socketSession);
            }
        }

        return rs;
    }

    @Override
    public List<SocketInfo> createSocketInfo() {
        return socketInfos;
    }

    @Override
    public DelegateSocketIOServer getSocketServer(String serverId) {
        return socketInfos.stream().filter(it -> {
            WrapperConfiguration wrapperConfiguration = it.getWrapperConfiguration();
            String clientId = wrapperConfiguration.getClientId();
            if (clientId.equals(serverId)) {
                return true;
            }

            String contentPath = wrapperConfiguration.getContentPath();
            if (contentPath.equals(serverId)) {
                return true;
            }

            if (contentPath.endsWith(serverId)) {
                return true;
            }
            return false;
        }).map(SocketInfo::getServer).findFirst().orElse(null);
    }
}
