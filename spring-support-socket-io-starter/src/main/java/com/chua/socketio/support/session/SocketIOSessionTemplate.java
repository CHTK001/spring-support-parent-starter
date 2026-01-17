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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.chua.starter.common.support.logger.ModuleLog.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Socket.IO 会话模板实现
 * 基于 netty-socketio 实现，支持多房间配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Slf4j
public class SocketIOSessionTemplate implements SocketSessionTemplate {

    private static final Logger log = LoggerFactory.getLogger(SocketIOSessionTemplate.class);
    private final SocketProperties properties;
    private final List<SocketListener> listeners;
    private final Map<String, List<SocketIOSession>> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * 多服务器实例：clientId -> server
     */
    private final Map<String, DelegateSocketIOServer> servers = new ConcurrentHashMap<>();

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
            // 如果列表为空，清理空列表以节省内存
            if (sessions.isEmpty()) {
                sessionCache.remove(clientId);
            }
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
                try {
                    session.send(event, msg);
                } catch (Exception e) {
                    log.warn("[SocketIO] 广播消息失败: sessionId={}, event={}", 
                            session.getId(), event, e);
                }
            }
        }
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            for (SocketIOSession session : sessions) {
                SocketUser user = session.getUser();
                if (user != null && userId.equals(user.getUserId())) {
                    try {
                        session.send(event, msg);
                    } catch (Exception e) {
                        log.warn("[SocketIO] 发送消息给用户失败: userId={}, sessionId={}, event={}", 
                                userId, session.getId(), event, e);
                    }
                }
            }
        }
    }

    @Override
    public List<SocketSession> getOnlineSessions() {
        List<SocketSession> result = new java.util.ArrayList<>();
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            result.addAll(sessions);
        }
        return result;
    }

    @Override
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        List<SocketIOSession> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        if (roomId == null || roomId.isEmpty()) {
            return new java.util.ArrayList<>(sessions);
        }

        // 根据 roomId 过滤会话
        List<SocketSession> result = new java.util.ArrayList<>();
        for (SocketIOSession session : sessions) {
            Object sessionRoomId = session.getAttribute("roomId");
            if (roomId.equals(sessionRoomId)) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public List<SocketUser> getOnlineUsers(String type) {
        List<SocketUser> result = new java.util.ArrayList<>();
        java.util.Set<String> userIds = new java.util.HashSet<>();
        
        List<SocketIOSession> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        for (SocketIOSession session : sessions) {
            SocketUser user = session.getUser();
            if (user != null && userIds.add(user.getUserId())) {
                result.add(user);
            }
        }
        return result;
    }

    @Override
    public int getOnlineCount() {
        int count = 0;
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            count += (int) sessions.stream()
                    .filter(SocketSession::isConnected)
                    .count();
        }
        return count;
    }

    @Override
    public void start() {
        if (!servers.isEmpty()) {
            log.warn("[SocketIO] 服务已启动");
            return;
        }

        List<SocketProperties.Room> rooms = properties.getRoom();
        
        // 如果没有配置 room，不启动任何服务，作为空实现
        if (rooms == null || rooms.isEmpty()) {
            log.info("[SocketIO] 未配置 room，跳过服务启动");
            return;
        }

        log.info("[SocketIO] 开始启动服务...");
        // 启动每个 room 配置的服务
        for (SocketProperties.Room room : rooms) {
            if (!room.isEnable()) {
                log.info("[SocketIO] 房间 {} 未启用，跳过", room.getClientId());
                continue;
            }

            String clientId = room.getClientId();
            String host = room.getActualHost(properties.getHost());
            // 如果 port 是正数，直接使用；如果是负数，需要主端口（这里使用默认值 9000）
            int port = room.getPort() >= 0 ? room.getPort() : room.getActualPort(9000);
            String contextPath = room.getContextPath();
            log.info("[SocketIO] 启动服务: 客户端ID={}, 地址={}, 端口={}, 上下文={}", 
                    clientId, host, port, contextPath);
            startServer(clientId, host, port, contextPath);
        }
        log.info("[SocketIO] 服务启动完成 [{}]", enabled());
    }

    /**
     * 启动单个服务实例
     *
     * @param clientId    客户端标识
     * @param host        主机地址
     * @param port        端口
     * @param contextPath 上下文路径
     */
    private void startServer(String clientId, String host, int port, String contextPath) {
        try {
            Configuration configuration = createConfiguration(host, port, contextPath);
            DelegateSocketIOServer server = new DelegateSocketIOServer(configuration);

            // 连接事件
            server.addConnectListener(client -> {
                SocketIOSession session = new SocketIOSession(client, properties);
                session.setAttribute("clientId", clientId);
                session.setAttribute("contextPath", contextPath);
                save(clientId, session);
                log.debug("[SocketIO] 客户端连接: clientId={}, sessionId={}", clientId, client.getSessionId());
            });

            // 断开连接事件
            server.addDisconnectListener(client -> {
                String sessionId = client.getSessionId().toString();
                SocketSession session = getSession(sessionId);
                if (session != null) {
                    remove(clientId, session);
                }
                log.debug("[SocketIO] 客户端断开: clientId={}, sessionId={}", clientId, sessionId);
            });

            server.start();
            servers.put(clientId, server);
            log.info("[SocketIO] 服务启动 {} - {} context={}", 
                    success(), address(host, port), contextPath);

        } catch (Exception e) {
            log.error("[SocketIO] 服务启动 {} - clientId={}", failed(), clientId, e);
            throw new RuntimeException("Socket.IO 服务启动失败: " + clientId, e);
        }
    }

    @Override
    public void stop() {
        if (servers.isEmpty()) {
            return;
        }

        for (Map.Entry<String, DelegateSocketIOServer> entry : servers.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("[SocketIO] 服务已停止: clientId={}", entry.getKey());
            } catch (Exception e) {
                log.error("[SocketIO] 服务停止失败: clientId={}", entry.getKey(), e);
            }
        }
        servers.clear();
        sessionCache.clear();
    }

    /**
     * 获取指定 clientId 的服务器实例
     *
     * @param clientId 客户端标识
     * @return 服务器实例，不存在返回 null
     */
    public DelegateSocketIOServer getServer(String clientId) {
        return servers.get(clientId);
    }

    /**
     * 获取所有服务器实例
     *
     * @return 服务器实例映射
     */
    public Map<String, DelegateSocketIOServer> getServers() {
        return servers;
    }

    /**
     * 创建 Socket.IO 配置
     *
     * @param host        主机地址
     * @param port        端口
     * @param contextPath 上下文路径
     * @return 配置对象
     */
    private Configuration createConfiguration(String host, int port, String contextPath) {
        Configuration configuration = new Configuration();

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);
        configuration.setSocketConfig(socketConfig);

        configuration.setHostname(host);
        configuration.setPort(port);
        configuration.setContext(contextPath);
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
