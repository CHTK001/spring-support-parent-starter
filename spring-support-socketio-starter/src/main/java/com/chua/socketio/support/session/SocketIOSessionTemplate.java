package com.chua.socketio.support.session;

import com.chua.socket.support.SocketListener;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Socket.IO 会话模板实现
 * 基于 netty-socketio 实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Slf4j
public class SocketIOSessionTemplate implements SocketSessionTemplate {

    private final SocketProperties properties;
    private final List<SocketListener> listeners;
    private final Map<String, List<SocketIOSession>> sessionCache = new ConcurrentHashMap<>();
    private DelegateSocketIOServer server;

    public SocketIOSessionTemplate(SocketProperties properties, List<SocketListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
    }

    @Override
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof SocketIOSession ioSession) {
            sessionCache.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(ioSession);
        }
        return session;
    }

    @Override
    public void remove(String clientId, SocketSession session) {
        List<SocketIOSession> sessions = sessionCache.get(clientId);
        if (sessions != null && session instanceof SocketIOSession ioSession) {
            sessions.remove(ioSession);
        }
    }

    @Override
    public SocketSession getSession(String sessionId) {
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            for (SocketIOSession session : sessions) {
                if (session.getId().equals(sessionId)) {
                    return session;
                }
            }
        }
        return null;
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        SocketSession session = getSession(sessionId);
        if (session != null) {
            session.send(event, msg);
        }
    }

    @Override
    public void broadcast(String event, String msg) {
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            for (SocketIOSession session : sessions) {
                session.send(event, msg);
            }
        }
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            for (SocketIOSession session : sessions) {
                SocketUser user = session.getUser();
                if (user != null && userId.equals(user.getUserId())) {
                    session.send(event, msg);
                }
            }
        }
    }

    @Override
    public List<SocketSession> getOnlineSessions() {
        List<SocketSession> result = new LinkedList<>();
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            result.addAll(sessions);
        }
        return result;
    }

    @Override
    public List<SocketUser> getOnlineUsers(String type) {
        List<SocketUser> result = new LinkedList<>();
        List<String> userIds = new LinkedList<>();
        
        List<SocketIOSession> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        for (SocketIOSession session : sessions) {
            SocketUser user = session.getUser();
            if (user != null && !userIds.contains(user.getUserId())) {
                result.add(user);
                userIds.add(user.getUserId());
            }
        }
        return result;
    }

    @Override
    public int getOnlineCount() {
        int count = 0;
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            count += sessions.size();
        }
        return count;
    }

    @Override
    public void start() {
        if (server != null) {
            log.warn("[SocketIO] 服务已启动");
            return;
        }

        try {
            Configuration configuration = createConfiguration();
            server = new DelegateSocketIOServer(configuration);

            // 连接事件
            server.addConnectListener(client -> {
                SocketIOSession session = new SocketIOSession(client, properties);
                save("default", session);
                log.debug("[SocketIO] 客户端连接: {}", client.getSessionId());
            });

            // 断开连接事件
            server.addDisconnectListener(client -> {
                String sessionId = client.getSessionId().toString();
                SocketSession session = getSession(sessionId);
                if (session != null) {
                    remove("default", session);
                }
                log.debug("[SocketIO] 客户端断开: {}", sessionId);
            });

            server.start();
            log.info("[SocketIO] 服务启动成功，端口: {}", properties.getPort());

        } catch (Exception e) {
            log.error("[SocketIO] 服务启动失败", e);
            throw new RuntimeException("Socket.IO 服务启动失败", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                server.stop();
                log.info("[SocketIO] 服务已停止");
            } catch (Exception e) {
                log.error("[SocketIO] 服务停止失败", e);
            } finally {
                server = null;
            }
        }
    }

    /**
     * 创建 Socket.IO 配置
     */
    private Configuration createConfiguration() {
        Configuration configuration = new Configuration();

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);
        configuration.setSocketConfig(socketConfig);

        configuration.setHostname(properties.getHost());
        configuration.setPort(properties.getPort());
        configuration.setTransports(Transport.POLLING, Transport.WEBSOCKET);

        configuration.setBossThreads(properties.getBossCount());
        configuration.setWorkerThreads(properties.getWorkCount());
        configuration.setAllowCustomRequests(properties.isAllowCustomRequests());

        configuration.setPingTimeout(properties.getPingTimeout());
        configuration.setPingInterval(properties.getPingInterval());
        configuration.setMaxFramePayloadLength(properties.getMaxFrameSize());
        configuration.setMaxHttpContentLength(properties.getMaxFrameSize());

        configuration.setWebsocketCompression(true);
        configuration.setHttpCompression(true);
        configuration.setJsonSupport(new JacksonJsonSupport());
        configuration.setAddVersionHeader(true);
        configuration.setUseLinuxNativeEpoll(properties.isUseLinuxNativeEpoll());

        return configuration;
    }
}
