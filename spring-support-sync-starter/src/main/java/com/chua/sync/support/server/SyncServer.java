package com.chua.sync.support.server;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.sync.support.pojo.ClientInfo;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 同步协议服务端管理器
 * <p>
 * 支持多实例服务端，管理多个 SyncServerInstance
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Slf4j
public class SyncServer implements InitializingBean, DisposableBean {

    /**
     * 系统主题 (兼容旧版本)
     */
    public static final String TOPIC_HEALTH = SyncServerInstance.TOPIC_HEALTH;
    public static final String TOPIC_RESPONSE = SyncServerInstance.TOPIC_RESPONSE;
    public static final String TOPIC_CONNECT = SyncServerInstance.TOPIC_CONNECT;
    public static final String TOPIC_DISCONNECT = SyncServerInstance.TOPIC_DISCONNECT;

    @Getter
    private final SyncProperties syncProperties;

    /**
     * 服务实例映射: name -> instance
     */
    @Getter
    private final Map<String, SyncServerInstance> instances = new ConcurrentHashMap<>();

    /**
     * 所有客户端信息 (汇总所有实例)
     * 内部存储，使用 sessionId 作为 key
     */
    private final Map<String, ClientInfo> allClientsInternal = new ConcurrentHashMap<>();

    /**
     * 消息处理器
     */
    private final List<SyncMessageHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * 连接监听器
     */
    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>();

    /**
     * 断开监听器
     */
    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>();

    public SyncServer(SyncProperties syncProperties) {
        this.syncProperties = syncProperties;
        loadHandlers();
    }

    /**
     * 加载消息处理器
     */
    private void loadHandlers() {
        ServiceProvider<SyncMessageHandler> serviceProvider = ServiceProvider.of(SyncMessageHandler.class);
        handlers.addAll(serviceProvider.collect());
        handlers.sort(Comparator.comparingInt(SyncMessageHandler::getOrder));
        log.info("[Sync] 加载 {} 个消息处理器", highlight(handlers.size()));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!syncProperties.isServerEnabled()) {
            log.info("[Sync] 服务端 [{}]", disabled());
            return;
        }

        startAllInstances();
    }

    /**
     * 启动所有服务实例
     */
    private void startAllInstances() {
        List<SyncProperties.ServerInstance> instanceConfigs = syncProperties.getServer().getEffectiveInstances();

        for (SyncProperties.ServerInstance config : instanceConfigs) {
            if (!config.isEnable()) {
                log.info("[Sync] 实例 {} [{}]", config.getName(), disabled());
                continue;
            }

            SyncServerInstance instance = new SyncServerInstance(config, syncProperties);

            // 注册消息处理器
            registerHandlersToInstance(instance, config);

            // 添加客户端监听器
            instance.addConnectListener((sessionId, clientInfo) -> {
                allClientsInternal.put(sessionId, clientInfo);
                notifyConnectListeners(sessionId, clientInfo);
            });
            instance.addDisconnectListener((sessionId, clientInfo) -> {
                allClientsInternal.remove(sessionId);
                notifyDisconnectListeners(sessionId, clientInfo);
            });

            // 启动实例
            instance.start();
            instances.put(config.getName(), instance);
        }

        log.info("[Sync] 启动 {} 个服务实例 [{}]", highlight(instances.size()), enabled());
    }

    /**
     * 注册处理器到实例
     */
    private void registerHandlersToInstance(SyncServerInstance instance, SyncProperties.ServerInstance config) {
        // 合并全局配置和实例配置的 topics
        Map<String, String> topics = new HashMap<>(syncProperties.getTopics());
        topics.putAll(config.getTopics());

        for (Map.Entry<String, String> entry : topics.entrySet()) {
            String topic = entry.getKey();
            String handlerName = entry.getValue();

            for (SyncMessageHandler handler : handlers) {
                if (handlerName.equals(handler.getName()) || handler.supports(topic)) {
                    instance.registerHandler(topic, handler);
                }
            }
        }
    }

    /**
     * 动态注册消息处理器到所有实例
     */
    public void registerHandler(String topic, SyncMessageHandler handler) {
        handlers.add(handler);
        for (SyncServerInstance instance : instances.values()) {
            instance.registerHandler(topic, handler);
        }
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

    /**
     * 向指定会话发送消息 (自动查找所属实例)
     */
    public void send(String sessionId, String topic, Object data) {
        for (SyncServerInstance instance : instances.values()) {
            if (instance.getSession(sessionId) != null) {
                instance.send(sessionId, topic, data);
                return;
            }
        }
        log.warn("[SyncServer] 未找到会话: {}", sessionId);
    }

    /**
     * 广播消息到所有实例的所有客户端
     */
    public void broadcast(String topic, Object data) {
        for (SyncServerInstance instance : instances.values()) {
            instance.broadcast(topic, data);
        }
    }

    /**
     * 广播消息到指定实例
     */
    public void broadcast(String instanceName, String topic, Object data) {
        SyncServerInstance instance = instances.get(instanceName);
        if (instance != null) {
            instance.broadcast(topic, data);
        }
    }

    /**
     * 向指定节点发送消息（通过 IP 和端口定位）
     *
     * @param host  目标 IP 地址
     * @param port  目标端口
     * @param topic 消息主题
     * @param data  消息数据
     * @return 是否发送成功
     */
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

    /**
     * 向指定节点发送消息（通过应用名定位）
     *
     * @param appName 应用名称
     * @param topic   消息主题
     * @param data    消息数据
     * @return 发送成功的客户端数量
     */
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

    /**
     * 向指定节点发送消息（通过 IP、端口和应用名定位）
     *
     * @param host    目标 IP 地址
     * @param port    目标端口（<=0 时忽略）
     * @param appName 应用名称（null 时忽略）
     * @param topic   消息主题
     * @param data    消息数据
     * @return 是否发送成功
     */
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

    /**
     * 获取指定实例
     */
    public SyncServerInstance getInstance(String name) {
        return instances.get(name);
    }

    /**
     * 获取默认实例
     */
    public SyncServerInstance getDefaultInstance() {
        return instances.get("default");
    }

    /**
     * 获取总连接数
     */
    public int getConnectionCount() {
        return instances.values().stream().mapToInt(SyncServerInstance::getConnectionCount).sum();
    }

    /**
     * 根据地址或应用名查找客户端ID
     *
     * @param host    IP地址 (可为null)
     * @param port    端口 (<=0 忽略)
     * @param appName 应用名 (可为null)
     * @return 客户端ID，未找到返回 null
     */
    public String findClientId(String host, int port, String appName) {
        for (Map.Entry<String, ClientInfo> entry : allClientsInternal.entrySet()) {
            ClientInfo clientInfo = entry.getValue();
            
            // 通过 IP 和端口匹配
            if (host != null && host.equals(clientInfo.getClientIpAddress())) {
                if (port <= 0 || port == clientInfo.getClientPort()) {
                    return entry.getKey();
                }
            }
            
            // 通过应用名匹配
            if (appName != null && appName.equals(clientInfo.getClientApplicationName())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 根据应用名查找所有客户端ID
     *
     * @param appName 应用名
     * @return 客户端ID列表
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
     * 根据 IP 查找所有客户端ID
     *
     * @param host IP地址
     * @return 客户端ID列表
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
     * 获取所有会话ID
     */
    public Set<String> getConnectedSessions() {
        Set<String> all = new HashSet<>();
        for (SyncServerInstance instance : instances.values()) {
            all.addAll(instance.getSessionIds());
        }
        return all;
    }

    /**
     * 获取所有客户端信息（根据 IP + 端口去重）
     * <p>
     * 如果存在相同 IP + 端口的多个客户端，保留最新心跳时间的客户端
     * </p>
     *
     * @return 去重后的客户端信息 Map，key 为 "ip:port"
     */
    public Map<String, ClientInfo> getAllClients() {
        if (allClientsInternal.isEmpty()) {
            return new HashMap<>();
        }
        
        // 根据 IP + 端口去重，保留最新心跳时间的客户端
        return allClientsInternal.values().stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                // 使用 IP:端口 作为去重 key
                                client -> client.getClientIpAddress() + ":" + client.getClientPort(),
                                client -> client,
                                // 如果有重复，保留最新心跳时间的客户端
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
     * 获取所有客户端信息（原始数据，不去重）
     *
     * @return 原始客户端信息 Map，key 为 sessionId
     */
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
