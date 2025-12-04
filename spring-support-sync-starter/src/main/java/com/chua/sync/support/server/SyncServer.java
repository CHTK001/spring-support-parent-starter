package com.chua.sync.support.server;

import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.sync.*;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同步协议服务端
 * <p>
 * 基于 SyncProtocol 实现长连接通信，支持：
 * <ul>
 *   <li>订阅配置的所有主题</li>
 *   <li>通过 SPI 加载处理器处理消息</li>
 *   <li>客户端连接管理</li>
 *   <li>心跳保活</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Slf4j
public class SyncServer implements InitializingBean, DisposableBean {

    /**
     * 系统主题
     */
    public static final String TOPIC_HEALTH = "sync/health";
    public static final String TOPIC_RESPONSE = "sync/response";
    public static final String TOPIC_CONNECT = "sync/connect";
    public static final String TOPIC_DISCONNECT = "sync/disconnect";

    private final SyncProperties syncProperties;

    /**
     * 同步协议服务端
     */
    @Getter
    private SyncProtocolServer protocolServer;

    /**
     * 定时同步调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 消息处理器映射：topic -> handlers
     */
    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>();

    /**
     * 客户端会话映射
     */
    private final Map<String, SyncSession> sessionMap = new ConcurrentHashMap<>();

    public SyncServer(SyncProperties syncProperties) {
        this.syncProperties = syncProperties;
        loadHandlers();
    }

    /**
     * 加载所有消息处理器
     */
    private void loadHandlers() {
        ServiceProvider<SyncMessageHandler> serviceProvider = ServiceProvider.of(SyncMessageHandler.class);
        List<SyncMessageHandler> allHandlers = new ArrayList<>(serviceProvider.collect());
        allHandlers.sort(Comparator.comparingInt(SyncMessageHandler::getOrder));

        // 按配置的 topics 映射处理器
        Map<String, String> topics = syncProperties.getTopics();
        for (Map.Entry<String, String> entry : topics.entrySet()) {
            String topic = entry.getKey();
            String handlerName = entry.getValue();

            List<SyncMessageHandler> handlers = allHandlers.stream()
                    .filter(h -> handlerName.equals(h.getName()) || h.supports(topic))
                    .toList();

            if (!handlers.isEmpty()) {
                handlerMap.put(topic, handlers);
                log.info("[Sync服务端] 主题 {} 加载了 {} 个处理器", topic, handlers.size());
            }
        }

        log.info("[Sync服务端] 共加载 {} 个主题的处理器", handlerMap.size());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!syncProperties.isEnable() || !"server".equalsIgnoreCase(syncProperties.getType())) {
            log.info("[Sync服务端] 未启用");
            return;
        }

        startServer();
        startScheduler();
    }

    /**
     * 启动同步协议服务器
     */
    private void startServer() {
        try {
            // 构建协议配置
            ProtocolSetting protocolSetting = ProtocolSetting.builder()
                    .protocol(syncProperties.getProtocol())
                    .host(syncProperties.getServerHost())
                    .port(syncProperties.getServerPort())
                    .heartbeat(syncProperties.isHeartbeat())
                    .heartbeatInterval(syncProperties.getHeartbeatInterval())
                    .connectTimeoutMillis(syncProperties.getConnectTimeout())
                    .build();

            // 创建同步协议实例
            SyncProtocol protocol = SyncProtocol.create(syncProperties.getProtocol(), protocolSetting);

            // 创建服务端
            protocolServer = protocol.createServer(protocolSetting);

            // 添加连接监听器
            protocolServer.addConnectionListener(new ServerConnectionListener());

            // 添加消息监听器
            protocolServer.addMessageListener(new ServerMessageListener());

            // 启动服务器
            protocolServer.start();

            log.info("[Sync服务端] 启动成功，协议: {}，地址: {}:{}",
                    syncProperties.getProtocol(), syncProperties.getServerHost(), syncProperties.getServerPort());
        } catch (Exception e) {
            log.error("[Sync服务端] 启动失败", e);
        }
    }

    /**
     * 连接监听器
     */
    private class ServerConnectionListener implements SyncConnectionListener {

        @Override
        public void onConnect(SyncSession session) {
            sessionMap.put(session.getSessionId(), session);
            log.info("[Sync服务端] 客户端连接: sessionId={}", session.getSessionId());

            // 广播连接事件给其他在线客户端
            broadcastToOthers(session.getSessionId(), TOPIC_CONNECT, Map.of(
                    "sessionId", session.getSessionId(),
                    "event", "connect",
                    "onlineCount", sessionMap.size(),
                    "timestamp", System.currentTimeMillis()
            ));
        }

        @Override
        public void onDisconnect(SyncSession session) {
            sessionMap.remove(session.getSessionId());
            log.info("[Sync服务端] 客户端断开: sessionId={}", session.getSessionId());

            // 广播断开事件给其他在线客户端
            broadcastToOthers(session.getSessionId(), TOPIC_DISCONNECT, Map.of(
                    "sessionId", session.getSessionId(),
                    "event", "disconnect",
                    "onlineCount", sessionMap.size(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * 消息监听器
     */
    private class ServerMessageListener implements SyncMessageListener {

        @Override
        @SuppressWarnings("unchecked")
        public void onMessage(SyncSession session, SyncMessage message) {
            String topic = message.getTopic();
            Object data = message.getData();
            log.debug("[Sync服务端] 收到消息: sessionId={}, topic={}", session.getSessionId(), topic);

            // 处理系统主题
            if (TOPIC_HEALTH.equals(topic)) {
                handleHealthCheck(session);
                return;
            }

            // 获取主题对应的处理器
            List<SyncMessageHandler> handlers = handlerMap.get(topic);
            if (handlers == null || handlers.isEmpty()) {
                log.warn("[Sync服务端] 未找到主题 {} 的处理器", topic);
                return;
            }

            // 循环执行所有处理器
            Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : Map.of("data", data);
            for (SyncMessageHandler handler : handlers) {
                try {
                    Object result = handler.handle(topic, session.getSessionId(), dataMap);
                    if (result != null) {
                        // 发送响应
                        protocolServer.send(session.getSessionId(), TOPIC_RESPONSE, Map.of(
                                "topic", topic,
                                "handler", handler.getName(),
                                "code", 200,
                                "data", result
                        ));
                    }
                    log.debug("[Sync服务端] 处理器 {} 处理主题 {} 完成", handler.getName(), topic);
                } catch (Exception e) {
                    log.error("[Sync服务端] 处理器 {} 处理主题 {} 失败", handler.getName(), topic, e);
                    protocolServer.send(session.getSessionId(), TOPIC_RESPONSE, Map.of(
                            "topic", topic,
                            "handler", handler.getName(),
                            "code", 500,
                            "message", e.getMessage()
                    ));
                }
            }
        }
    }

    /**
     * 处理健康检查
     */
    private void handleHealthCheck(SyncSession session) {
        protocolServer.send(session.getSessionId(), TOPIC_HEALTH, Map.of(
                "status", "UP",
                "service", "sync-server",
                "connections", protocolServer.getConnectionCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 向指定会话发送消息
     *
     * @param sessionId 会话ID
     * @param topic     主题
     * @param data      数据
     */
    public void send(String sessionId, String topic, Object data) {
        if (protocolServer == null) {
            log.warn("[Sync服务端] 未启动，无法发送消息");
            return;
        }
        protocolServer.send(sessionId, topic, data);
    }

    /**
     * 广播消息到所有客户端
     *
     * @param topic 主题
     * @param data  数据
     */
    public void broadcast(String topic, Object data) {
        if (protocolServer == null) {
            log.warn("[Sync服务端] 未启动，无法广播消息");
            return;
        }
        protocolServer.broadcast(topic, Map.of(
                "data", data,
                "timestamp", System.currentTimeMillis()
        ));
        log.info("[Sync服务端] 已广播消息到 {} 个客户端", protocolServer.getConnectionCount());
    }

    /**
     * 广播消息到除指定会话外的其他客户端
     *
     * @param excludeSessionId 排除的会话ID
     * @param topic            主题
     * @param data             数据
     */
    public void broadcastToOthers(String excludeSessionId, String topic, Object data) {
        if (protocolServer == null) {
            log.warn("[Sync服务端] 未启动，无法广播消息");
            return;
        }
        
        sessionMap.forEach((sessionId, session) -> {
            if (!sessionId.equals(excludeSessionId)) {
                protocolServer.send(sessionId, topic, data);
            }
        });
        log.debug("[Sync服务端] 已广播 {} 消息到其他 {} 个客户端", topic, sessionMap.size() - 1);
    }

    /**
     * 启动定时同步调度器
     */
    private void startScheduler() {
        SyncProperties.ScheduleSync scheduleSync = syncProperties.getScheduleSync();
        if (!scheduleSync.isEnable()) {
            log.info("[Sync服务端] 定时同步未启用");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "sync-server-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
                this::scheduledSync,
                scheduleSync.getInitialDelay(),
                scheduleSync.getInterval(),
                TimeUnit.SECONDS);

        log.info("[Sync服务端] 定时同步调度器启动成功，间隔: {}秒", scheduleSync.getInterval());
    }

    /**
     * 定时同步任务
     */
    private void scheduledSync() {
        // 子类可重写此方法实现定时同步逻辑
        log.debug("[Sync服务端] 执行定时同步任务");
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return protocolServer != null ? protocolServer.getConnectionCount() : 0;
    }

    /**
     * 获取所有已连接的会话ID
     */
    public Set<String> getConnectedSessions() {
        return new HashSet<>(sessionMap.keySet());
    }

    @Override
    public void destroy() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[Sync服务端] 定时同步调度器已关闭");
        }

        if (protocolServer != null) {
            protocolServer.stop();
            log.info("[Sync服务端] 已关闭");
        }

        sessionMap.clear();
    }
}
