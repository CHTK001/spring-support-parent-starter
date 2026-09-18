package com.chua.socketio.support.session;

import com.chua.socket.support.SocketListener;
import com.chua.socket.support.handler.EventListenerProcessor;
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
* 套接字.IO 会话模板实现
* 基于 Netty-套接字.IO 实现，支持多房间配置
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
@Slf4j
public class SocketIOSessionTemplate implements SocketSessionTemplate {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(SocketIOSessionTemplate.class);
    /** 属性 */
    private final SocketProperties properties;
    /** 监听器 */
    private final List<SocketListener> listeners;
    private final Map<String, List<SocketIOSession>> sessionCache = new ConcurrentHashMap<>(); // 会话缓存
    /** 事件监听器处理器 */
    private final EventListenerProcessor eventListenerProcessor;
    
    /**
    * 多服务器实例：客户端标识 -> 服务端
    */
    private final Map<String, DelegateSocketIOServer> servers = new ConcurrentHashMap<>();

    /**
    * 创建 套接字io会话template 实例
    * @param properties 属性
    * @param listeners 列表
    * @param listeners 监听器
    */
    public SocketIOSessionTemplate(SocketProperties properties, List<SocketListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
        this.eventListenerProcessor = new EventListenerProcessor(listeners);
    }

    @Override
    /** 保存 */
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof SocketIOSession ioSession) {
            sessionCache.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(ioSession);
        }
        return session;
    }

    @Override
    /** 移除 */
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
    /** 获取会话 */
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
    /** 发送 */
    public void send(String sessionId, String event, String msg) {
        SocketSession session = getSession(sessionId);
        if (session != null) {
            session.send(event, msg);
        }
    }

    @Override
    /** Broadcast */
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
        // 诊断：登记 session 数（会话登记断点排查）
        if (log.isDebugEnabled()) {
            int n = sessionCache.values().stream().mapToInt(List::size).sum();
            log.debug("[SocketIO] 广播完成 event={}, 已登记 session={}", event, n);
        }
    }

    @Override
    /** 发送转为用户 */
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
    /** 获取online会话 */
    public List<SocketSession> getOnlineSessions() {
        List<SocketSession> result = new java.util.ArrayList<>();
        for (List<SocketIOSession> sessions : sessionCache.values()) {
            result.addAll(sessions);
        }
        return result;
    }

    @Override
    /** 获取online会话 */
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        List<SocketIOSession> sessions = sessionCache.get(type);
        if (sessions == null) {
            return Collections.emptyList();
        }

        if (roomId == null || roomId.isEmpty()) {
            return new java.util.ArrayList<>(sessions);
        }

 // 根据 roomid 过滤会话
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
    /** 获取online用户 */
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
    /** 获取Online计算数量 */
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
    /** 开始 */
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
 // 如果 端口 是正数，直接使用；如果是负数，需要主端口（这里使用默认值 9000）
            int port = room.getPort() >= 0 ? room.getPort() : room.getActualPort(9000);
            String contextPath = room.getContextPath();
            log.info("[SocketIO] 启动服务: 客户端ID={}, 地址={}, 端口={}, 上下文={}", 
                    clientId, host, port, contextPath);
            /**
    * 启动服务端。
    * @param clientId 客户端标识
    * @param host 主机
    * @param port 端口
    * @param contextPath 上下文路径
    */
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
                /**
                * 保存。
                * @param clientId 客户端标识
                * @param session 会话
                */
                save(clientId, session);
                log.debug("[SocketIO] 客户端连接: clientId={}, sessionId={}", clientId, client.getSessionId());
            });

            // 断开连接事件
            server.addDisconnectListener(client -> {
                String sessionId = client.getSessionId().toString();
                SocketSession session = getSession(sessionId);
                if (session != null) {
                    /**
                    * 移除。
                    * @param clientId 客户端标识
                    * @param session 会话
                    */
                    remove(clientId, session);
                }
                log.debug("[SocketIO] 客户端断开: clientId={}, sessionId={}", clientId, sessionId);
            });

            /**
            * 注册事件监听器。
            * @param server 服务端
            * @param clientId 客户端标识
            * @param contextPath 上下文路径
            */
            registerEventListeners(server, clientId, contextPath);

            server.start();
            servers.put(clientId, server);
            log.info("[SocketIO] 服务启动 {} - {} context={}", 
                    success(), address(host, port), contextPath);

        } catch (Exception e) {
            log.error("[SocketIO] 服务启动 {} - clientId={}", failed(), clientId, e);
            /**
            * runtime异常。
            * @param clientId 客户端标识
            * @param e e
            * @return runtime异常的结果
            */
            throw new RuntimeException("Socket.IO 服务启动失败: " + clientId, e);
        }
    }

    /**
    * 注册事件监听器
    *
    * @param server 服务端
    * @param clientId 客户端标识
    * @param contextPath 上下文路径
    */
    private void registerEventListeners(DelegateSocketIOServer server, String clientId, String contextPath) {
        for (String eventName : eventListenerProcessor.getRegisteredEvents()) {
            server.addEventListener(eventName, String.class, (client, data, ackSender) -> {
                SocketSession session = getSession(client.getSessionId().toString());
                if (session == null) {
                    SocketIOSession ioSession = new SocketIOSession(client, properties);
                    ioSession.setAttribute("clientId", clientId);
                    ioSession.setAttribute("contextPath", contextPath);
                    /**
                    * 保存。
                    * @param clientId 客户端标识
                    * @param ioSession io会话
                    */
                    save(clientId, ioSession);
                    session = ioSession;
                }
                eventListenerProcessor.fireEvent(eventName, session, data);
            });
            log.debug("[SocketIO] 注册入站事件: clientId={}, event={}", clientId, eventName);
        }
    }

    @Override
    /** 停止 */
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
    * 获取指定 客户端标识 的服务器实例
    *
    * @param clientId 客户端标识
    * @return 服务器实例，不存在返回 空
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
    * 创建 套接字.IO 配置
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
