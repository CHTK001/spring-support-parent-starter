package com.chua.starter.sync.support.server;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 同步服务器，负责客户端连接管理、健康检查与消息分发。
 *
 * <p>通过 SPI 加载 {@link SyncMessageHandler} 处理同步消息，并监听客户端连接/断开事件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class SyncServer implements InitializingBean, DisposableBean {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(SyncServer.class);

    /**
     * Topic_健康
    */
    public static final String TOPIC_HEALTH = SyncServerInstance.TOPIC_HEALTH;
    /**
     * Topic_响应
    */
    public static final String TOPIC_RESPONSE = SyncServerInstance.TOPIC_RESPONSE;
    /**
     * Topic_连接
    */
    public static final String TOPIC_CONNECT = SyncServerInstance.TOPIC_CONNECT;
    /**
     * Topic_断开连接
    */
    public static final String TOPIC_DISCONNECT = SyncServerInstance.TOPIC_DISCONNECT;

    /**
     * 同步属性
    */
    private final SyncProperties syncProperties;

    private final Map<String, SyncServerInstance> instances = new ConcurrentHashMap<>(); // instances

    private final Map<String, ClientInfo> allClientsInternal = new ConcurrentHashMap<>(); // 全部客户端内部

    /**
     * 处理器
    */
    private final List<com.chua.common.support.network.sync.SyncMessageHandler> handlers = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>(); // 连接监听器

    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>(); // 断开连接监听器

    /**
     * 创建 同步服务端 实例
     * @param syncProperties 同步属性
     */
    public SyncServer(SyncProperties syncProperties) {
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    /**
     * 获取同步属性
     *
     * @return 获取同步属性的结果
     */
    public SyncProperties getSyncProperties() {
        return syncProperties;
    }

    /**
     * 获取Instances
     *
     * @return 获取instances的结果
     */
    public Map<String, SyncServerInstance> getInstances() {
        return instances;
    }

    @Override
    /**
     * 之后属性设置
     * @return 返回值
     * @throws Exception 异常
    */
    public void afterPropertiesSet() throws Exception {
        if (!syncProperties.isServerEnabled()) {
            log.info("[Sync] 服务端 [{}]", disabled());
            return;
        }
        /**
         * 启动全部instances。
         */
        startAllInstances();
    }

    /**
     * 开始全部instances
    */
    private void startAllInstances() {
        List<SyncProperties.ServerInstance> instanceConfigs = syncProperties.getServer().getEffectiveInstances();

        for (SyncProperties.ServerInstance config : instanceConfigs) {
            if (!config.isEnable()) {
                log.info("[Sync] 实例 {} [{}]", config.getName(), disabled());
                continue;
            }

            SyncServerInstance instance = new SyncServerInstance(config, syncProperties);
            /**
             * 注册处理器转为instance。
             * @param instance instance
             * @param config 配置
             */
            registerHandlersToInstance(instance, config);

            instance.addConnectListener((sessionId, clientInfo) -> {
                allClientsInternal.put(sessionId, clientInfo);
                /**
                 * 通知连接监听器。
                 * @param sessionId 会话标识
                 * @param clientInfo 客户端信息
                 */
                notifyConnectListeners(sessionId, clientInfo);
            });
            instance.addDisconnectListener((sessionId, clientInfo) -> {
                allClientsInternal.remove(sessionId);
                /**
                 * 通知断开连接监听器。
                 * @param sessionId 会话标识
                 * @param clientInfo 客户端信息
                 */
                notifyDisconnectListeners(sessionId, clientInfo);
            });

            instance.start();
            instances.put(config.getName(), instance);
        }

        log.info("[Sync] 启动 {} 个服务实例 [{}]", highlight(instances.size()), enabled());
    }

    /**
     * 注册处理器转为instance
     *
     * @param instance instance
     * @param config 配置
     */
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

    /**
     * 注册处理器
     *
     * @param topic topic
     * @param handler 处理器
     */
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

    /**
     * 注册主题处理器（业务 SPI 版本）。
     * <p>
     * 与客户端 {@link com.chua.starter.sync.support.client.SyncClient#registerHandler(String, com.chua.starter.sync.support.spi.SyncMessageHandler)}
     * 暴露一致的处理器注册入口，内部将 SPI 处理器适配到底层会话分发，
     * 并注入来源客户端标识供业务处理器反查会话上下文。
     * </p>
     *
     * @param topic   主题
     * @param handler 业务处理器
     */
    public void registerHandler(String topic, com.chua.starter.sync.support.spi.SyncMessageHandler handler) {
        if (topic == null || topic.isBlank() || handler == null) {
            return;
        }

        com.chua.common.support.network.sync.SyncMessageHandler adapter = (t, message) -> {
            Map<String, Object> dataMap = new HashMap<>();
            if (message instanceof Map<?, ?> rawMap) {
                rawMap.forEach((key, value) -> dataMap.put(String.valueOf(key), value));
            } else {
                dataMap.put("data", message);
            }
            Object source = dataMap.remove(SyncServerInstance.SYNC_MESSAGE_SOURCE_CLIENT_ID);
            String sessionId = source == null ? null : String.valueOf(source);
            handler.handle(topic, sessionId, dataMap);
        };
        /**
         * 注册处理器。
         * @param topic topic
         * @param adapter 适配器
         */
        registerHandler(topic, adapter);
    }

    /**
     * 添加连接监听器
     *
     * @param listener 监听器
     */
    public void addConnectListener(BiConsumer<String, ClientInfo> listener) {
        connectListeners.add(listener);
    }

    /**
     * 添加断开监听器
     *
     * @param listener 监听器
     */
    public void addDisconnectListener(BiConsumer<String, ClientInfo> listener) {
        disconnectListeners.add(listener);
    }

    /**
     * 通知连接监听器
     *
     * @param sessionId 会话标识
     * @param clientInfo 客户端信息
     */
    private void notifyConnectListeners(String sessionId, ClientInfo clientInfo) {
        for (BiConsumer<String, ClientInfo> listener : connectListeners) {
            try {
                listener.accept(sessionId, clientInfo);
            } catch (Exception e) {
                log.error("[Sync] 连接监听器执行失败", e);
            }
        }
    }

    /**
     * 通知断开监听器
     *
     * @param sessionId 会话标识
     * @param clientInfo 客户端信息
     */
    private void notifyDisconnectListeners(String sessionId, ClientInfo clientInfo) {
        for (BiConsumer<String, ClientInfo> listener : disconnectListeners) {
            try {
                listener.accept(sessionId, clientInfo);
            } catch (Exception e) {
                log.error("[Sync] 断开监听器执行失败", e);
            }
        }
    }

    /**
     * 发送
     *
     * @param sessionId 会话标识
     * @param topic topic
     * @param data 数据
     */
    public void send(String sessionId, String topic, Object data) {
        for (SyncServerInstance instance : instances.values()) {
            if (instance.containsClient(sessionId)) {
                instance.send(sessionId, topic, data);
                return;
            }
        }
        log.warn("[SyncServer] 未找到会话: {}", sessionId);
    }

    /**
     * Broadcast
     *
     * @param topic topic
     * @param data 数据
     */
    public void broadcast(String topic, Object data) {
        for (SyncServerInstance instance : instances.values()) {
            instance.broadcast(topic, data);
        }
    }

    /**
     * 发布
     *
     * @param topic topic
     * @param data 数据
     */
    public void publish(String topic, Object data) {
        /**
         * broadcast。
         * @param topic topic
         * @param data 数据
         */
        broadcast(topic, data);
    }

    /**
     * Broadcast
     *
     * @param instanceName instance名称
     * @param topic topic
     * @param data 数据
     */
    public void broadcast(String instanceName, String topic, Object data) {
        SyncServerInstance instance = instances.get(instanceName);
        if (instance != null) {
            instance.broadcast(topic, data);
        }
    }

    /**
     * 发布
     *
     * @param host 主机
     * @param port 端口
     * @param topic topic
     * @param data 数据
     * @return 发布的结果
     */
    public boolean publish(String host, int port, String topic, Object data) {
        String clientId = findClientId(host, port, null);
        if (clientId != null) {
            /**
             * 发送。
             * @param clientId 客户端标识
             * @param topic topic
             * @param data 数据
             */
            send(clientId, topic, data);
            log.debug("[SyncServer] 向节点 {}:{} 发送消息, topic={}", host, port, topic);
            return true;
        }
        log.warn("[SyncServer] 未找到节点: {}:{}", host, port);
        return false;
    }

    /**
     * 发布byapp名称
     *
     * @param appName app名称
     * @param topic topic
     * @param data 数据
     * @return 发布byapp名称的结果
     */
    public int publishByAppName(String appName, String topic, Object data) {
        List<String> clientIds = findClientIdsByAppName(appName);
        if (clientIds.isEmpty()) {
            log.warn("[SyncServer] 未找到应用: {}", appName);
            return 0;
        }
        for (String clientId : clientIds) {
            /**
             * 发送。
             * @param clientId 客户端标识
             * @param topic topic
             * @param data 数据
             */
            send(clientId, topic, data);
        }
        log.debug("[SyncServer] 向应用 {} 的 {} 个节点发送消息, topic={}", appName, clientIds.size(), topic);
        return clientIds.size();
    }

    /**
     * 发布
     *
     * @param host 主机
     * @param port 端口
     * @param appName app名称
     * @param topic topic
     * @param data 数据
     * @return 发布的结果
     */
    public boolean publish(String host, int port, String appName, String topic, Object data) {
        String clientId = findClientId(host, port, appName);
        if (clientId != null) {
            /**
             * 发送。
             * @param clientId 客户端标识
             * @param topic topic
             * @param data 数据
             */
            send(clientId, topic, data);
            log.debug("[SyncServer] 向节点 {}:{}({}) 发送消息, topic={}", host, port, appName, topic);
            return true;
        }
        log.warn("[SyncServer] 未找到节点: {}:{}({})", host, port, appName);
        return false;
    }

    /**
     * 获取Instance
     *
     * @param name 名称
     * @return 获取instance的结果
     */
    public SyncServerInstance getInstance(String name) {
        return instances.get(name);
    }

    /**
     * 获取默认instance
     *
     * @return 获取默认instance的结果
     */
    public SyncServerInstance getDefaultInstance() {
        return instances.get("default");
    }

    /**
     * 获取Connection计算数量
     *
     * @return 获取connection数量的结果
     */
    public int getConnectionCount() {
        return instances.values().stream().mapToInt(SyncServerInstance::getConnectionCount).sum();
    }

    /**
     * 查找客户端标识
     *
     * @param host 主机
     * @param port 端口
     * @param appName app名称
     * @return find客户端id的结果
     */
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

    /**
     * 查找客户端标识byapp名称
     *
     * @param appName app名称
     * @return find客户端标识byapp名称的结果
     */
    public List<String> findClientIdsByAppName(String appName) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            if (appName.equals(entry.getValue().getClientApplicationName())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 查找客户端标识by主机
     *
     * @param host 主机
     * @return find客户端标识by主机的结果
     */
    public List<String> findClientIdsByHost(String host) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            if (host.equals(entry.getValue().getClientIpAddress())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 查找客户端标识bymetadata
     *
     * @param metadataKey metadataKey
     * @param metadataValue metadata值
     * @return find客户端标识bymetadata的结果
     */
    public List<String> findClientIdsByMetadata(String metadataKey, Object metadataValue) {
        return findClientIds(clientInfo -> {
            Map<String, Object> metadata = clientInfo.getClientMetadata();
            if (metadata == null || metadataKey == null || metadataKey.isBlank()) {
                return false;
            }
            return matchesValue(metadata.get(metadataKey), metadataValue);
        });
    }

    /**
     * 查找客户端标识
     *
     * @param filter 过滤器
     * @return find客户端标识的结果
     */
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

    /**
     * 发布bymetadata
     *
     * @param metadataKey metadataKey
     * @param metadataValue metadata值
     * @param topic topic
     * @param data 数据
     * @return 发布bymetadata的结果
     */
    public int publishByMetadata(String metadataKey, Object metadataValue, String topic, Object data) {
        return publishToClients(clientInfo -> {
            Map<String, Object> metadata = clientInfo.getClientMetadata();
            if (metadata == null || metadataKey == null || metadataKey.isBlank()) {
                return false;
            }
            return matchesValue(metadata.get(metadataKey), metadataValue);
        }, topic, data);
    }

    /**
     * 发布转为客户端
     *
     * @param filter 过滤器
     * @param topic topic
     * @param data 数据
     * @return 发布转为客户端的结果
     */
    public int publishToClients(Predicate<ClientInfo> filter, String topic, Object data) {
        List<String> clientIds = findClientIds(filter);
        if (clientIds.isEmpty()) {
            return 0;
        }

        for (String clientId : clientIds) {
            /**
             * 发送。
             * @param clientId 客户端标识
             * @param topic topic
             * @param data 数据
             */
            send(clientId, topic, data);
        }
        return clientIds.size();
    }

    /**
     * 匹配值
     *
     * @param actual actual
     * @param expected 期望
     * @return 匹配值的结果
     */
    private boolean matchesValue(Object actual, Object expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return Objects.equals(String.valueOf(actual), String.valueOf(expected));
    }

    /**
     * 获取连接会话
     *
     * @return 获取连接会话的结果
     */
    public Set<String> getConnectedSessions() {
        Set<String> all = new HashSet<>();
        for (SyncServerInstance instance : instances.values()) {
            all.addAll(instance.getSessionIds());
        }
        return all;
    }

    /**
     * 获取全部客户端
     *
     * @return 获取全部客户端的结果
     */
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

    /**
     * 获取全部客户端raw
     *
     * @return 获取全部客户端raw的结果
     */
    public Map<String, ClientInfo> getAllClientsRaw() {
        return new HashMap<>(allClientsInternal);
    }

    @Override
    /**
     * 销毁
     * @return 返回值
     * @throws Exception 异常
    */
    public void destroy() throws Exception {
        for (SyncServerInstance instance : instances.values()) {
            instance.stop();
        }
        instances.clear();
        allClientsInternal.clear();

        log.info("[SyncServer] 已停止所有服务实例");
    }
}

