package com.chua.socketio.support.session;

import com.chua.common.support.printer.TablePrinter;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import lombok.extern.slf4j.Slf4j;
import static com.chua.starter.common.support.logger.ModuleLog.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        List<SocketIOSession> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        if (roomId == null || roomId.isEmpty()) {
            return new LinkedList<>(sessions);
        }

        // 根据 roomId 过滤会话
        List<SocketSession> result = new LinkedList<>();
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

        TablePrinter printer = new TablePrinter();
        printer.addRow("客户端ID","地址", "端口", "上下文");
        // 启动每个 room 配置的服务
        for (SocketProperties.Room room : rooms) {
            if (!room.isEnable()) {
                log.info("[SocketIO] 房间 {} 未启用，跳过", room.getClientId());
                continue;
            }

            String clientId = room.getClientId();
            String host = room.getActualHost(properties.getHost());
            int port = room.getActualPort(room.getPort());
            String contextPath = room.getContextPath();
            printer.addRow(clientId, host, port, contextPath);
            startServer(clientId, host, port, contextPath);
        }
        log.info("[SocketIO] 服务启动 [{}]\n{}", enabled(), printer.draw());
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
