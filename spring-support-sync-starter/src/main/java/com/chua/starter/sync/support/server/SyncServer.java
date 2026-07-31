package com.chua.starter.sync.support.server;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public class SyncServer implements InitializingBean, DisposableBean {

    public static final String TOPIC_HEALTH = SyncServerInstance.TOPIC_HEALTH;
    public static final String TOPIC_RESPONSE = SyncServerInstance.TOPIC_RESPONSE;
    public static final String TOPIC_CONNECT = SyncServerInstance.TOPIC_CONNECT;
    public static final String TOPIC_DISCONNECT = SyncServerInstance.TOPIC_DISCONNECT;

    @Getter
    private final SyncProperties syncProperties;

    @Getter
    private final Map<String, SyncServerInstance> instances = new ConcurrentHashMap<>();

    private final Map<String, ClientInfo> allClientsInternal = new ConcurrentHashMap<>();

    private final List<com.chua.common.support.network.sync.SyncMessageHandler> handlers = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>();

    public SyncServer(SyncProperties syncProperties) {
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!syncProperties.isServerEnabled()) {
            log.info("[Sync] 服务端 [{}]", disabled());
            return;
        }
        startAllInstances();
    }

    private void startAllInstances() {
        List<SyncProperties.ServerInstance> instanceConfigs = syncProperties.getServer().getEffectiveInstances();

        for (SyncProperties.ServerInstance config : instanceConfigs) {
            if (!config.isEnable()) {
                log.info("[Sync] 实例 {} [{}]", config.getName(), disabled());
                continue;
            }

            SyncServerInstance instance = new SyncServerInstance(config, syncProperties);
            registerHandlersToInstance(instance, config);

            instance.addConnectListener((sessionId, clientInfo) -> {
                allClientsInternal.put(sessionId, clientInfo);
                notifyConnectListeners(sessionId, clientInfo);
            });
            instance.addDisconnectListener((sessionId, clientInfo) -> {
                allClientsInternal.remove(sessionId);
                notifyDisconnectListeners(sessionId, clientInfo);
            });

            instance.start();
            instances.put(config.getName(), instance);
        }

        log.info("[Sync] 启动 {} 个服务实例 [{}]", highlight(instances.size()), enabled());
    }

    private void registerHandlersToInstance(SyncServerInstance instance, SyncProperties.ServerInstance config) {
        Map<String, String> topics = new HashMap<>(syncProperties.getTopics());
        if (config.getTopics() != null) {
            topics.putAll(config.getTopics());
        }

        for (Map.Entry<String, String> entry : topics.entrySet()) {
            String topic = entry.getKey();
            for (com.chua.common.support.network.sync.SyncMessageHandler handler : handlers) {
                instance.registerHandler(topic, handler);
            }
        }
    }

    public void registerHandler(String topic, com.chua.common.support.network.sync.SyncMessageHandler handler) {
        if (topic == null || topic.isBlank() || handler == null) {
            return;
        }

        boolean exists = handlers.stream().anyMatch(existing -> existing == handler);
        if (!exists) {
            handlers.add(handler);
        }
        for (SyncServerInstance instance : instances.values()) {
            instance.registerHandler(topic, handler);
        }
    }

    public void addConnectListener(BiConsumer<String, ClientInfo> listener) {
        connectListeners.add(listener);
    }

    public void addDisconnectListener(BiConsumer<String, ClientInfo> listener) {
        disconnectListeners.add(listener);
    }

    private void notifyConnectListeners(String sessionId, ClientInfo clientInfo) {
        for (BiConsumer<String, ClientInfo> listener : connectListeners) {
            try {
                listener.accept(sessionId, clientInfo);
            } catch (Exception e) {
                log.error("[Sync] 连接监听器执行失败", e);
            }
        }
    }

    private void notifyDisconnectListeners(String sessionId, ClientInfo clientInfo) {
        for (BiConsumer<String, ClientInfo> listener : disconnectListeners) {
            try {
                listener.accept(sessionId, clientInfo);
            } catch (Exception e) {
                log.error("[Sync] 断开监听器执行失败", e);
            }
        }
    }

    public void send(String sessionId, String topic, Object data) {
        for (SyncServerInstance instance : instances.values()) {
            if (instance.containsClient(sessionId)) {
                instance.send(sessionId, topic, data);
                return;
            }
        }
        log.warn("[SyncServer] 未找到会话: {}", sessionId);
    }

    public void broadcast(String topic, Object data) {
        for (SyncServerInstance instance : instances.values()) {
            instance.broadcast(topic, data);
        }
    }

    public void broadcast(String instanceName, String topic, Object data) {
        SyncServerInstance instance = instances.get(instanceName);
        if (instance != null) {
            instance.broadcast(topic, data);
        }
    }

    public boolean publish(String host, int port, String topic, Object data) {
        String clientId = findClientId(host, port, null);
        if (clientId != null) {
            send(clientId, topic, data);
            log.debug("[SyncServer] 向节点 {}:{} 发送消息, topic={}", host, port, topic);
            return true;
        }
        log.warn("[SyncServer] 未找到节点: {}:{}", host, port);
        return false;
    }

    public int publishByAppName(String appName, String topic, Object data) {
        List<String> clientIds = findClientIdsByAppName(appName);
        if (clientIds.isEmpty()) {
            log.warn("[SyncServer] 未找到应用: {}", appName);
            return 0;
        }
        for (String clientId : clientIds) {
            send(clientId, topic, data);
        }
        log.debug("[SyncServer] 向应用 {} 的 {} 个节点发送消息, topic={}", appName, clientIds.size(), topic);
        return clientIds.size();
    }

    public boolean publish(String host, int port, String appName, String topic, Object data) {
        String clientId = findClientId(host, port, appName);
        if (clientId != null) {
            send(clientId, topic, data);
            log.debug("[SyncServer] 向节点 {}:{}({}) 发送消息, topic={}", host, port, appName, topic);
            return true;
        }
        log.warn("[SyncServer] 未找到节点: {}:{}({})", host, port, appName);
        return false;
    }

    public SyncServerInstance getInstance(String name) {
        return instances.get(name);
    }

    public SyncServerInstance getDefaultInstance() {
        return instances.get("default");
    }

    public int getConnectionCount() {
        return instances.values().stream().mapToInt(SyncServerInstance::getConnectionCount).sum();
    }

    public String findClientId(String host, int port, String appName) {
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            ClientInfo clientInfo = entry.getValue();

            if (host != null && host.equals(clientInfo.getClientIpAddress())) {
                if (port <= 0 || port == clientInfo.getClientPort()) {
                    return entry.getKey();
                }
            }

            if (appName != null && appName.equals(clientInfo.getClientApplicationName())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public List<String> findClientIdsByAppName(String appName) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            if (appName.equals(entry.getValue().getClientApplicationName())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public List<String> findClientIdsByHost(String host) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            if (host.equals(entry.getValue().getClientIpAddress())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public List<String> findClientIdsByMetadata(String metadataKey, Object metadataValue) {
        return findClientIds(clientInfo -> {
            Map<String, Object> metadata = clientInfo.getClientMetadata();
            if (metadata == null || metadataKey == null || metadataKey.isBlank()) {
                return false;
            }
            return matchesValue(metadata.get(metadataKey), metadataValue);
        });
    }

    public List<String> findClientIds(Predicate<ClientInfo> filter) {
        if (filter == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            ClientInfo clientInfo = entry.getValue();
            if (clientInfo != null && filter.test(clientInfo)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public int publishByMetadata(String metadataKey, Object metadataValue, String topic, Object data) {
        return publishToClients(clientInfo -> {
            Map<String, Object> metadata = clientInfo.getClientMetadata();
            if (metadata == null || metadataKey == null || metadataKey.isBlank()) {
                return false;
            }
            return matchesValue(metadata.get(metadataKey), metadataValue);
        }, topic, data);
    }

    public int publishToClients(Predicate<ClientInfo> filter, String topic, Object data) {
        List<String> clientIds = findClientIds(filter);
        if (clientIds.isEmpty()) {
            return 0;
        }

        for (String clientId : clientIds) {
            send(clientId, topic, data);
        }
        return clientIds.size();
    }

    private boolean matchesValue(Object actual, Object expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return Objects.equals(String.valueOf(actual), String.valueOf(expected));
    }

    public Set<String> getConnectedSessions() {
        Set<String> all = new HashSet<>();
        for (SyncServerInstance instance : instances.values()) {
            all.addAll(instance.getSessionIds());
        }
        return all;
    }

    public Map<String, ClientInfo> getAllClients() {
        if (allClientsInternal.isEmpty()) {
            return new HashMap<>();
        }

        return allClientsInternal.values().stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                client -> client.getClientIpAddress() + ":" + client.getClientPort(),
                                client -> client,
                                (existing, replacement) -> {
                                    long existingTime = existing.getClientLastHeartbeatTime();
                                    long replacementTime = replacement.getClientLastHeartbeatTime();
                                    return replacementTime > existingTime ? replacement : existing;
                                },
                                LinkedHashMap::new
                        ),
                        result -> result
                ));
    }

    public Map<String, ClientInfo> getAllClientsRaw() {
        return new HashMap<>(allClientsInternal);
    }

    @Override
    public void destroy() throws Exception {
        for (SyncServerInstance instance : instances.values()) {
            instance.stop();
        }
        instances.clear();
        allClientsInternal.clear();

        log.info("[SyncServer] 已停止所有服务实例");
    }
}
