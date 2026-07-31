package com.chua.starter.sync.support.server;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class SyncServerInstance {

    private static final Logger log = LoggerFactory.getLogger(SyncServerInstance.class);

    public static final String TOPIC_HEALTH = "sync/health";
    public static final String TOPIC_RESPONSE = "sync/response";
    public static final String TOPIC_CONNECT = "sync/connect";
    public static final String TOPIC_DISCONNECT = "sync/disconnect";
    public static final String TOPIC_CLIENT_REGISTER = "sync/client/register";
    public static final String TOPIC_CLIENT_HEARTBEAT = "sync/client/heartbeat";
    public static final String TOPIC_CLIENT_OFFLINE = "sync/client/offline";

    private final SyncProperties.ServerInstance instanceConfig;

    private final SyncProperties syncProperties;

    private SyncServer syncServer;

    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>();

    private final Map<String, ClientInfo> clientInfoMap = new ConcurrentHashMap<>();

    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>();

    private volatile boolean running = false;

    private boolean listenersAttached = false;

    public SyncServerInstance(SyncProperties.ServerInstance instanceConfig, SyncProperties syncProperties) {
        this.instanceConfig = SyncProperties.ServerInstance.copyOf(instanceConfig);
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    public SyncProperties.ServerInstance getInstanceConfig() {
        return instanceConfig;
    }

    public SyncProperties getSyncProperties() {
        return syncProperties;
    }

    public SyncServer getSyncServer() {
        return syncServer;
    }

    public Map<String, ClientInfo> getClientInfoMap() {
        return clientInfoMap;
    }

    public boolean isRunning() {
        return running;
    }

    public void registerHandler(String topic, com.chua.common.support.network.sync.SyncMessageHandler handler) {
        if (topic == null || topic.isBlank() || handler == null) {
            return;
        }
        List<com.chua.common.support.network.sync.SyncMessageHandler> handlers = handlerMap.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        if (handlers.stream().noneMatch(h -> h == handler)) {
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
        syncServer.addListener(new InternalServerListener());
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

        com.chua.common.support.spi.ServiceProvider<SyncServer> provider =
                com.chua.common.support.spi.ServiceProvider.of(SyncServer.class);
        return provider.getNewExtension(protocol, setting);
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
        syncServer.publish(topic, data);
    }

    public void broadcastToOthers(String excludeClientId, String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        List<String> clients = syncServer.getConnectedClients();
        for (String clientId : clients) {
            if (!Objects.equals(clientId, excludeClientId)) {
                syncServer.send(clientId, topic, data);
            }
        }
    }

    public int getConnectionCount() {
        return syncServer != null ? syncServer.getConnectedClients().size() : 0;
    }

    public Set<String> getSessionIds() {
        return syncServer != null ? new HashSet<>(syncServer.getConnectedClients()) : new HashSet<>();
    }

    public boolean containsClient(String clientId) {
        return syncServer != null && clientId != null && syncServer.getConnectedClients().contains(clientId);
    }

    private class InternalServerListener implements SyncServerListener {
        @Override
        public void onClientConnected(String clientId, java.util.Map<String, Object> metadata) {
            log.info("[SyncServer:{}] 客户端连接: clientId={}", instanceConfig.getName(), clientId);
            broadcastToOthers(clientId, TOPIC_CONNECT, Map.of(
                    "clientId", clientId, "event", "connect",
                    "onlineCount", getConnectionCount(), "timestamp", System.currentTimeMillis()));
        }

        @Override
        public void onClientDisconnected(String clientId) {
            ClientInfo clientInfo = clientInfoMap.remove(clientId);
            log.info("[SyncServer:{}] 客户端断开: clientId={}", instanceConfig.getName(), clientId);
            for (BiConsumer<String, ClientInfo> listener : disconnectListeners) {
                try {
                    listener.accept(clientId, clientInfo);
                } catch (Exception e) {
                    log.error("[SyncServer:{}] 断开监听器执行失败", instanceConfig.getName(), e);
                }
            }
            broadcastToOthers(clientId, TOPIC_DISCONNECT, Map.of(
                    "clientId", clientId, "event", "disconnect",
                    "onlineCount", getConnectionCount(), "timestamp", System.currentTimeMillis()));
        }

        @Override
        public void onMessage(String clientId, String topic, Object message) {
            handleServerMessage(clientId, topic, message);
        }

        @Override
        public void onError(String clientId, Throwable cause) {
            log.error("[SyncServer:{}] 客户端错误: clientId={}", instanceConfig.getName(), clientId, cause);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleServerMessage(String clientId, String topic, Object data) {
        if (handleSystemTopic(clientId, topic, data)) {
            return;
        }
        List<SyncMessageHandler> handlers = handlerMap.get(topic);
        if ((handlers == null || handlers.isEmpty()) && syncProperties.getTopics().containsKey(topic)) {
            handlers = handlerMap.get(topic);
        }
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : Map.of("data", data);
        for (com.chua.common.support.network.sync.SyncMessageHandler handler : handlers) {
            try {
                handler.handle(topic, dataMap);
            } catch (Exception e) {
                log.error("[SyncServer:{}] 处理器执行失败: topic={}", instanceConfig.getName(), topic, e);
                send(clientId, TOPIC_RESPONSE, Map.of(
                        "requestId", dataMap.getOrDefault("requestId", ""),
                        "topic", topic, "success", false, "code", 500,
                        "message", e.getMessage(), "timestamp", System.currentTimeMillis()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean handleSystemTopic(String clientId, String topic, Object data) {
        if (TOPIC_HEALTH.equals(topic)) {
            send(clientId, TOPIC_HEALTH, Map.of("status", "UP", "instance", instanceConfig.getName(),
                    "connections", getConnectionCount(), "timestamp", System.currentTimeMillis()));
            return true;
        }
        if (isClientRegisterTopic(topic)) {
            handleClientRegister(clientId, data);
            return true;
        }
        if (isClientHeartbeatTopic(topic)) {
            handleClientHeartbeat(clientId);
            return true;
        }
        if (isClientOfflineTopic(topic)) {
            handleClientOffline(clientId);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void handleClientRegister(String clientId, Object data) {
        try {
            ClientInfo clientInfo = new ClientInfo();
            if (data instanceof Map<?, ?> map) {
                copyMapToClientInfo((Map<String, Object>) map, clientInfo);
            }
            clientInfoMap.put(clientId, clientInfo);
            log.info("[SyncServer:{}] 客户端注册: clientId={}, app={}, ip={}:{}",
                    instanceConfig.getName(), clientId, clientInfo.getClientApplicationName(),
                    clientInfo.getClientIpAddress(), clientInfo.getClientPort());
            for (BiConsumer<String, ClientInfo> listener : connectListeners) {
                try {
                    listener.accept(clientId, clientInfo);
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

    private void handleClientHeartbeat(String clientId) {
        ClientInfo clientInfo = clientInfoMap.get(clientId);
        if (clientInfo != null) {
            clientInfo.setClientLastHeartbeatTime(System.currentTimeMillis());
            clientInfo.setClientOnline(true);
        }
    }

    private void handleClientOffline(String clientId) {
        ClientInfo clientInfo = clientInfoMap.get(clientId);
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
