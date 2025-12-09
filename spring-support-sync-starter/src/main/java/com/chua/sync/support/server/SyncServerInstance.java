package com.chua.sync.support.server;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.sync.*;
import com.chua.common.support.utils.MapUtils;
import com.chua.sync.support.pojo.ClientInfo;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.spi.SyncMessageHandler;
import com.fasterxml.jackson.databind.util.BeanUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import static com.chua.common.support.utils.ArrayUtils.EMPTY_STRING_ARRAY;

/**
 * 同步服务端实例
 * <p>
 * 单个服务端实例，支持多个实例同时运行
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
public class SyncServerInstance {

    /**
     * 系统主题
     */
    public static final String TOPIC_HEALTH = "sync/health";
    public static final String TOPIC_RESPONSE = "sync/response";
    public static final String TOPIC_CONNECT = "sync/connect";
    public static final String TOPIC_DISCONNECT = "sync/disconnect";
    public static final String TOPIC_CLIENT_REGISTER = "sync/client/register";
    public static final String TOPIC_CLIENT_HEARTBEAT = "sync/client/heartbeat";
    public static final String TOPIC_CLIENT_OFFLINE = "sync/client/offline";

    /**
     * 实例配置
     */
    @Getter
    private final SyncProperties.ServerInstance instanceConfig;

    /**
     * 全局配置
     */
    private final SyncProperties syncProperties;

    /**
     * 协议服务端
     */
    @Getter
    private SyncProtocolServer protocolServer;

    /**
     * 消息处理器映射
     */
    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>();

    /**
     * 会话映射
     */
    private final Map<String, SyncSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * 客户端信息映射: clientId/sessionId -> ClientInfo
     */
    @Getter
    private final Map<String, ClientInfo> clientInfoMap = new ConcurrentHashMap<>();

    /**
     * 连接监听器
     */
    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>();

    /**
     * 断开监听器
     */
    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>();

    /**
     * 是否运行中
     */
    @Getter
    private volatile boolean running = false;

    public SyncServerInstance(SyncProperties.ServerInstance instanceConfig, SyncProperties syncProperties) {
        this.instanceConfig = instanceConfig;
        this.syncProperties = syncProperties;
    }

    /**
     * 注册消息处理器
     */
    public void registerHandler(String topic, SyncMessageHandler handler) {
        handlerMap.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * 添加连接监听器
     */
    public void addConnectListener(BiConsumer<String, ClientInfo> listener) {
        connectListeners.add(listener);
    }

    /**
     * 添加断开监听器
     */
    public void addDisconnectListener(BiConsumer<String, ClientInfo> listener) {
        disconnectListeners.add(listener);
    }

    /**
     * 启动服务
     */
    public void start() {
        if (running) {
            log.warn("[SyncServer:{}] 已在运行中", instanceConfig.getName());
            return;
        }

        try {
            String protocol = instanceConfig.getProtocol();

            ProtocolSetting protocolSetting = ProtocolSetting.builder()
                    .protocol(protocol)
                    .host(instanceConfig.getHost())
                    .port(instanceConfig.getPort())
                    .heartbeat(syncProperties.getServer().isHeartbeat())
                    .heartbeatInterval(syncProperties.getServer().getHeartbeatInterval())
                    .connectTimeoutMillis(syncProperties.getServer().getConnectTimeout())
                    .build();

            SyncProtocol syncProtocol = SyncProtocol.create(protocol, protocolSetting);
            protocolServer = syncProtocol.createServer(protocolSetting);

            // 添加监听器
            protocolServer.addConnectionListener(new InternalConnectionListener());
            protocolServer.addMessageListener(new InternalMessageListener());

            protocolServer.start();
            running = true;

            log.info("[SyncServer:{}] 启动成功, protocol={}, address={}:{}",
                    instanceConfig.getName(), protocol, instanceConfig.getHost(), instanceConfig.getPort());
        } catch (Exception e) {
            log.error("[SyncServer:{}] 启动失败", instanceConfig.getName(), e);
        }
    }

    /**
     * 停止服务
     */
    public void stop() {
        if (!running) return;

        try {
            if (protocolServer != null) {
                protocolServer.stop();
            }
            sessionMap.clear();
            clientInfoMap.clear();
            running = false;
            log.info("[SyncServer:{}] 已停止", instanceConfig.getName());
        } catch (Exception e) {
            log.error("[SyncServer:{}] 停止失败", instanceConfig.getName(), e);
        }
    }

    /**
     * 发送消息到指定会话
     */
    public void send(String sessionId, String topic, Object data) {
        if (protocolServer == null || !running) return;
        protocolServer.send(sessionId, topic, data);
    }

    /**
     * 广播消息
     */
    public void broadcast(String topic, Object data) {
        if (protocolServer == null || !running) return;
        protocolServer.broadcast(topic, data);
    }

    /**
     * 广播到除指定会话外的其他客户端
     */
    public void broadcastToOthers(String excludeSessionId, String topic, Object data) {
        sessionMap.forEach((sessionId, session) -> {
            if (!sessionId.equals(excludeSessionId)) {
                send(sessionId, topic, data);
            }
        });
    }

    /**
     * 获取连接数
     */
    public int getConnectionCount() {
        return sessionMap.size();
    }

    /**
     * 获取所有会话ID
     */
    public Set<String> getSessionIds() {
        return new HashSet<>(sessionMap.keySet());
    }

    /**
     * 获取会话
     */
    public SyncSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    // ==================== 内部监听器 ====================

    private class InternalConnectionListener implements SyncConnectionListener {

        @Override
        public void onConnect(SyncSession session) {
            String sessionId = session.getSessionId();
            sessionMap.put(sessionId, session);
            log.info("[SyncServer:{}] 客户端连接: sessionId={}", instanceConfig.getName(), sessionId);

            // 广播连接事件
            broadcastToOthers(sessionId, TOPIC_CONNECT, Map.of(
                    "sessionId", sessionId,
                    "event", "connect",
                    "onlineCount", sessionMap.size(),
                    "timestamp", System.currentTimeMillis()
            ));
        }

        @Override
        public void onDisconnect(SyncSession session) {
            String sessionId = session.getSessionId();
            sessionMap.remove(sessionId);
            ClientInfo clientInfo = clientInfoMap.remove(sessionId);
            log.info("[SyncServer:{}] 客户端断开: sessionId={}", instanceConfig.getName(), sessionId);

            // 通知监听器
            for (BiConsumer<String, ClientInfo> listener : disconnectListeners) {
                try {
                    listener.accept(sessionId, clientInfo);
                } catch (Exception e) {
                    log.error("[SyncServer:{}] 断开监听器执行失败", instanceConfig.getName(), e);
                }
            }

            // 广播断开事件
            broadcastToOthers(sessionId, TOPIC_DISCONNECT, Map.of(
                    "sessionId", sessionId,
                    "event", "disconnect",
                    "onlineCount", sessionMap.size(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    private class InternalMessageListener implements SyncMessageListener {

        @Override
        @SuppressWarnings("unchecked")
        public void onMessage(SyncSession session, SyncMessage message) {
            String sessionId = session.getSessionId();
            String topic = message.getTopic();
            Object data = message.getData();

            log.debug("[SyncServer:{}] 收到消息: sessionId={}, topic={}", instanceConfig.getName(), sessionId, topic);

            // 处理系统主题
            if (handleSystemTopic(session, topic, data)) {
                return;
            }

            // 获取处理器
            List<SyncMessageHandler> handlers = handlerMap.get(topic);
            // 如果实例没有配置，查找全局配置
            if ((handlers == null || handlers.isEmpty()) && syncProperties.getTopics().containsKey(topic)) {
                handlers = handlerMap.get(topic);
            }

            if (handlers == null || handlers.isEmpty()) {
                log.debug("[SyncServer:{}] 未找到主题处理器: {}", instanceConfig.getName(), topic);
                return;
            }

            // 执行处理器
            Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : Map.of("data", data);
            for (SyncMessageHandler handler : handlers) {
                try {
                    Object result = handler.handle(topic, sessionId, dataMap);
                    if (result != null) {
                        send(sessionId, TOPIC_RESPONSE, Map.of(
                                "requestId", dataMap.getOrDefault("requestId", ""),
                                "topic", topic,
                                "handler", handler.getName(),
                                "code", 200,
                                "data", result,
                                "timestamp", System.currentTimeMillis()
                        ));
                    }
                } catch (Exception e) {
                    log.error("[SyncServer:{}] 处理器执行失败: handler={}, topic={}",
                            instanceConfig.getName(), handler.getName(), topic, e);
                    send(sessionId, TOPIC_RESPONSE, Map.of(
                            "requestId", dataMap.getOrDefault("requestId", ""),
                            "topic", topic,
                            "code", 500,
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
                }
            }
        }

        @SuppressWarnings("unchecked")
        private boolean handleSystemTopic(SyncSession session, String topic, Object data) {
            String sessionId = session.getSessionId();

            switch (topic) {
                case TOPIC_HEALTH -> {
                    send(sessionId, TOPIC_HEALTH, Map.of(
                            "status", "UP",
                            "instance", instanceConfig.getName(),
                            "connections", getConnectionCount(),
                            "timestamp", System.currentTimeMillis()
                    ));
                    return true;
                }
                case TOPIC_CLIENT_REGISTER -> {
                    handleClientRegister(session, sessionId, data);
                    return true;
                }
                case TOPIC_CLIENT_HEARTBEAT -> {
                    handleClientHeartbeat(sessionId, data);
                    return true;
                }
                case TOPIC_CLIENT_OFFLINE -> {
                    handleClientOffline(sessionId, data);
                    return true;
                }
            }
            return false;
        }

        /**
         * 处理客户端注册
         * <p>
         * 将客户端信息存储到 clientInfoMap，并确保 sessionMap 中有对应的会话
         * </p>
         *
         * @param session   会话对象
         * @param sessionId 会话ID
         * @param data      注册数据
         */
        @SuppressWarnings("unchecked")
        private void handleClientRegister(SyncSession session, String sessionId, Object data) {
            try {
                ClientInfo clientInfo = ClientInfo.builder().build();
                BeanUtils.copyProperties(data, clientInfo);
                clientInfoMap.put(sessionId, clientInfo);
                
                // 确保 sessionMap 中有该会话（防止 onConnect 未正确触发的情况）
                if (!sessionMap.containsKey(sessionId) && session != null) {
                    sessionMap.put(sessionId, session);
                    log.debug("[SyncServer:{}] 补充 sessionMap: sessionId={}", instanceConfig.getName(), sessionId);
                }
                
                log.info("[SyncServer:{}] 客户端注册: sessionId={}, app={}, ip={}:{}",
                        instanceConfig.getName(), sessionId, clientInfo.getClientApplicationName(),
                        clientInfo.getClientIpAddress(), clientInfo.getClientPort());

                // 通知监听器
                for (BiConsumer<String, ClientInfo> listener : connectListeners) {
                    try {
                        listener.accept(sessionId, clientInfo);
                    } catch (Exception e) {
                        log.error("[SyncServer:{}] 连接监听器执行失败", instanceConfig.getName(), e);
                    }
                }
            } catch (Exception e) {
                log.error("[SyncServer:{}] 处理客户端注册失败", instanceConfig.getName(), e);
            }
        }

        @SuppressWarnings("unchecked")
        private void handleClientHeartbeat(String sessionId, Object data) {
            ClientInfo clientInfo = clientInfoMap.get(sessionId);
            if (clientInfo != null) {
                clientInfo.setClientLastHeartbeatTime(System.currentTimeMillis());
                clientInfo.setClientOnline(true);
            }
        }

        private void handleClientOffline(String sessionId, Object data) {
            ClientInfo clientInfo = clientInfoMap.get(sessionId);
            if (clientInfo != null) {
                clientInfo.setClientOnline(false);
            }
        }
    }
}
