package com.chua.starter.sync.support.server;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.sync.support.SyncConnectionListener;
import com.chua.starter.sync.support.SyncMessage;
import com.chua.starter.sync.support.SyncMessageListener;
import com.chua.starter.sync.support.SyncServer;
import com.chua.starter.sync.support.SyncSession;
import com.chua.starter.sync.support.SyncProtocol;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.spi.SyncMessageHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 同步服务端实例。
 *
 * <p>封装 {@link SyncServer} 的运行实例，管理连接、消息分发和客户端信息。</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
public class SyncServerInstance {

    public static final String TOPIC_HEALTH = "sync/health";
    public static final String TOPIC_RESPONSE = "sync/response";
    public static final String TOPIC_CONNECT = "sync/connect";
    public static final String TOPIC_DISCONNECT = "sync/disconnect";
    public static final String TOPIC_CLIENT_REGISTER = "sync/client/register";
    public static final String TOPIC_CLIENT_HEARTBEAT = "sync/client/heartbeat";
    public static final String TOPIC_CLIENT_OFFLINE = "sync/client/offline";

    @Getter
    private final SyncProperties.ServerInstance instanceConfig;

    private final SyncProperties syncProperties;

    @Getter
    private SyncServer syncServer;

    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, ClientInfo> clientInfoMap = new ConcurrentHashMap<>();

    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>();

    @Getter
    private volatile boolean running = false;

    /**
     * 监听器是否已注册到底层服务端。
     */
    private boolean listenersAttached = false;

    public SyncServerInstance(SyncProperties.ServerInstance instanceConfig, SyncProperties syncProperties) {
        this.instanceConfig = SyncProperties.ServerInstance.copyOf(instanceConfig);
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    public void registerHandler(String topic, SyncMessageHandler handler) {
        if (topic == null || topic.isBlank() || handler == null) {
            return;
        }
        List<SyncMessageHandler> handlers = handlerMap.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        boolean exists = handlers.stream()
                .anyMatch(existing -> existing == handler
                        || (existing.getClass().equals(handler.getClass())
                        && Objects.equals(existing.getName(), handler.getName())));
        if (!exists) {
            handlers.add(handler);
        }
    }

    public void addConnectListener(BiConsumer<String, ClientInfo> listener) {
        connectListeners.add(listener);
    }

    public void addDisconnectListener(BiConsumer<String, ClientInfo> listener) {
        disconnectListeners.add(listener);
    }

    public void setSyncServer(SyncServer syncServer) {
        this.syncServer = syncServer;
        attachListeners();
    }

    private void attachListeners() {
        if (listenersAttached || syncServer == null) {
            return;
        }
        syncServer.addConnectionListener(new InternalConnectionListener());
        syncServer.addMessageListener(new InternalMessageListener());
        listenersAttached = true;
    }

    public void start() {
        if (running) {
            log.warn("[SyncServer:{}] 已在运行中", instanceConfig.getName());
            return;
        }
        if (syncServer == null) {
            syncServer = createUnderlyingServer();
        }
        try {
            syncServer.start();
            running = true;
            log.info("[SyncServer:{}] 启动成功, protocol={}, address={}:{}",
                    instanceConfig.getName(), instanceConfig.getProtocol(),
                    instanceConfig.getHost(), instanceConfig.getPort());
        } catch (Exception e) {
            log.error("[SyncServer:{}] 启动失败", instanceConfig.getName(), e);
        }
    }

    private SyncServer createUnderlyingServer() {
        String protocol = instanceConfig.getProtocol();
        String host = instanceConfig.getHost() != null ? instanceConfig.getHost() : "0.0.0.0";
        int port = instanceConfig.getPort() > 0 ? instanceConfig.getPort() : 19380;

        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(host);
        setting.setPort(port);
        setting.setProtocol(protocol);

        com.chua.common.support.spi.ServiceProvider<SyncProtocol> provider =
                com.chua.common.support.spi.ServiceProvider.of(SyncProtocol.class);
        SyncProtocol syncProtocol = provider.getNewExtension(protocol, protocol);
        if (syncProtocol == null) {
            throw new IllegalArgumentException("Unsupported sync protocol: " + protocol);
        }
        return syncProtocol.createServer(setting);
    }

    public void stop() {
        if (!running) {
            return;
        }
        try {
            if (syncServer != null) {
                syncServer.stop();
            }
            clientInfoMap.clear();
            running = false;
            log.info("[SyncServer:{}] 已停止", instanceConfig.getName());
        } catch (Exception e) {
            log.error("[SyncServer:{}] 停止失败", instanceConfig.getName(), e);
        }
    }

    public void send(String sessionId, String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        syncServer.send(sessionId, topic, data);
    }

    public void broadcast(String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        syncServer.broadcast(topic, data);
    }

    public void broadcastToOthers(String excludeSessionId, String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        syncServer.broadcastToOthers(excludeSessionId, topic, data);
    }

    public int getConnectionCount() {
        return syncServer != null ? syncServer.getConnectionCount() : 0;
    }

    public Set<String> getSessionIds() {
        return syncServer != null ? syncServer.getSessionIds() : new HashSet<>();
    }

    public SyncSession getSession(String sessionId) {
        return syncServer != null ? syncServer.getSession(sessionId) : null;
    }

    private class InternalConnectionListener implements SyncConnectionListener {

        @Override
        public void onConnect(SyncSession session) {
            String sessionId = session.getSessionId();
            log.info("[SyncServer:{}] 客户端连接: sessionId={}", instanceConfig.getName(), sessionId);
            broadcastToOthers(sessionId, TOPIC_CONNECT, Map.of(
                    "sessionId", sessionId, "event", "connect",
                    "onlineCount", getConnectionCount(), "timestamp", System.currentTimeMillis()));
        }

        @Override
        public void onDisconnect(SyncSession session) {
            String sessionId = session.getSessionId();
            ClientInfo clientInfo = clientInfoMap.remove(sessionId);
            log.info("[SyncServer:{}] 客户端断开: sessionId={}", instanceConfig.getName(), sessionId);
            for (BiConsumer<String, ClientInfo> listener : disconnectListeners) {
                try {
                    listener.accept(sessionId, clientInfo);
                } catch (Exception e) {
                    log.error("[SyncServer:{}] 断开监听器执行失败", instanceConfig.getName(), e);
                }
            }
            broadcastToOthers(sessionId, TOPIC_DISCONNECT, Map.of(
                    "sessionId", sessionId, "event", "disconnect",
                    "onlineCount", getConnectionCount(), "timestamp", System.currentTimeMillis()));
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

            if (handleSystemTopic(sessionId, topic, data)) {
                return;
            }

            List<SyncMessageHandler> handlers = handlerMap.get(topic);
            if ((handlers == null || handlers.isEmpty()) && syncProperties.getTopics().containsKey(topic)) {
                handlers = handlerMap.get(topic);
            }
            if (handlers == null || handlers.isEmpty()) {
                log.debug("[SyncServer:{}] 未找到主题处理器: {}", instanceConfig.getName(), topic);
                return;
            }

            Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : Map.of("data", data);
            for (SyncMessageHandler handler : handlers) {
                try {
                    Object result = handler.handle(topic, sessionId, dataMap);
                    if (result != null) {
                        send(sessionId, TOPIC_RESPONSE, Map.of(
                                "requestId", dataMap.getOrDefault("requestId", ""),
                                "topic", topic, "handler", handler.getName(),
                                "success", true, "code", 200, "data", result,
                                "timestamp", System.currentTimeMillis()));
                    }
                } catch (Exception e) {
                    log.error("[SyncServer:{}] 处理器执行失败: handler={}, topic={}",
                            instanceConfig.getName(), handler.getName(), topic, e);
                    send(sessionId, TOPIC_RESPONSE, Map.of(
                            "requestId", dataMap.getOrDefault("requestId", ""),
                            "topic", topic, "success", false, "code", 500,
                            "message", e.getMessage(), "timestamp", System.currentTimeMillis()));
                }
            }
        }

        @SuppressWarnings("unchecked")
        private boolean handleSystemTopic(String sessionId, String topic, Object data) {
            if (TOPIC_HEALTH.equals(topic)) {
                send(sessionId, TOPIC_HEALTH, Map.of("status", "UP", "instance", instanceConfig.getName(),
                        "connections", getConnectionCount(), "timestamp", System.currentTimeMillis()));
                return true;
            }
            if (isClientRegisterTopic(topic)) {
                handleClientRegister(sessionId, data);
                return true;
            }
            if (isClientHeartbeatTopic(topic)) {
                handleClientHeartbeat(sessionId);
                return true;
            }
            if (isClientOfflineTopic(topic)) {
                handleClientOffline(sessionId);
                return true;
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private void handleClientRegister(String sessionId, Object data) {
            try {
                ClientInfo clientInfo = ClientInfo.builder().build();
                if (data instanceof Map<?, ?> map) {
                    copyMapToClientInfo((Map<String, Object>) map, clientInfo);
                }
                clientInfoMap.put(sessionId, clientInfo);
                log.info("[SyncServer:{}] 客户端注册: sessionId={}, app={}, ip={}:{}",
                        instanceConfig.getName(), sessionId, clientInfo.getClientApplicationName(),
                        clientInfo.getClientIpAddress(), clientInfo.getClientPort());
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

        private void copyMapToClientInfo(Map<String, Object> map, ClientInfo clientInfo) {
            if (map.containsKey("clientApplicationName")) {
                clientInfo.setClientApplicationName(String.valueOf(map.get("clientApplicationName")));
            }
            if (map.containsKey("clientIpAddress")) {
                clientInfo.setClientIpAddress(String.valueOf(map.get("clientIpAddress")));
            }
            if (map.containsKey("clientPort")) {
                clientInfo.setClientPort(Integer.parseInt(String.valueOf(map.get("clientPort"))));
            }
            if (map.containsKey("clientId")) {
                clientInfo.setClientId(String.valueOf(map.get("clientId")));
            }
        }

        private void handleClientHeartbeat(String sessionId) {
            ClientInfo clientInfo = clientInfoMap.get(sessionId);
            if (clientInfo != null) {
                clientInfo.setClientLastHeartbeatTime(System.currentTimeMillis());
                clientInfo.setClientOnline(true);
            }
        }

        private void handleClientOffline(String sessionId) {
            ClientInfo clientInfo = clientInfoMap.get(sessionId);
            if (clientInfo != null) {
                clientInfo.setClientOnline(false);
            }
        }

        private boolean isClientRegisterTopic(String topic) {
            return matchesClientTopic(topic, syncProperties.getClient().getRegisterTopic(), TOPIC_CLIENT_REGISTER);
        }

        private boolean isClientHeartbeatTopic(String topic) {
            return matchesClientTopic(topic, syncProperties.getClient().getHeartbeatTopic(), TOPIC_CLIENT_HEARTBEAT);
        }

        private boolean isClientOfflineTopic(String topic) {
            return matchesClientTopic(topic, syncProperties.getClient().getOfflineTopic(), TOPIC_CLIENT_OFFLINE);
        }

        private boolean matchesClientTopic(String actualTopic, String configuredTopic, String defaultTopic) {
            if (actualTopic == null || actualTopic.isBlank()) {
                return false;
            }
            if (Objects.equals(actualTopic, configuredTopic)) {
                return true;
            }
            return Objects.equals(actualTopic, defaultTopic);
        }
    }
}