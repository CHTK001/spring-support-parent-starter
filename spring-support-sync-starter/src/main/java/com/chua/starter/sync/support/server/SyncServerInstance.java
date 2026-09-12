package com.chua.starter.sync.support.server;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.security.SyncCryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
* @版本 1.0.0
* @since 2024/12/05
* @author CH
 */
public class SyncServerInstance {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(SyncServerInstance.class);

    /** Topic_健康 */
    public static final String TOPIC_HEALTH = "sync/health";
    /** Topic_响应 */
    public static final String TOPIC_RESPONSE = "sync/response";
    /** Topic_连接 */
    public static final String TOPIC_CONNECT = "sync/connect";
    /** Topic_断开连接 */
    public static final String TOPIC_DISCONNECT = "sync/disconnect";
    /** Topic_客户端_注册 */
    public static final String TOPIC_CLIENT_REGISTER = "sync/client/register";
    /** Topic_客户端_心跳 */
    public static final String TOPIC_CLIENT_HEARTBEAT = "sync/client/heartbeat";
    /** Topic_客户端_offline */
    public static final String TOPIC_CLIENT_OFFLINE = "sync/client/offline";

    /**
    * 业务消息中注入的源客户端标识 钥匙
    * <p>
    * 新版 {@link SyncMessageHandler#handle(String, Object)} 不携带会话上下文，
    * 服务端分发消息时将该 钥匙 写入消息体，处理器可据此反查源客户端信息。
    * </p>
     */
    public static final String SYNC_MESSAGE_SOURCE_CLIENT_ID = "__syncClientId__";

    /** 实例配置 */
    private final SyncProperties.ServerInstance instanceConfig;

    /** 同步属性 */
    private final SyncProperties syncProperties;

    /** 同步服务器 */
    private SyncServer syncServer;

    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>(); // 处理器映射

    private final Map<String, ClientInfo> clientInfoMap = new ConcurrentHashMap<>(); // 客户端信息映射

    private final List<BiConsumer<String, ClientInfo>> connectListeners = new CopyOnWriteArrayList<>(); // 连接监听器

    private final List<BiConsumer<String, ClientInfo>> disconnectListeners = new CopyOnWriteArrayList<>(); // 断开连接监听器

    private volatile boolean running = false; // running

    /** Listenersattached */
    private boolean listenersAttached = false;

    /**
    * 创建 同步服务端instance 实例
    * @param instanceConfig instance配置
    * @param syncProperties 同步属性
    * @param syncProperties 同步属性
     */
    public SyncServerInstance(SyncProperties.ServerInstance instanceConfig, SyncProperties syncProperties) {
        this.instanceConfig = SyncProperties.ServerInstance.copyOf(instanceConfig);
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    /**
    * 获取instance配置
    *
    * @return 获取instance配置的结果
     */
    public SyncProperties.ServerInstance getInstanceConfig() {
        return instanceConfig;
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
    * 获取同步服务端
    *
    * @return 获取同步服务端的结果
     */
    public SyncServer getSyncServer() {
        return syncServer;
    }

    /**
    * 获取客户端信息映射
    *
    * @return 获取客户端信息映射的结果
     */
    public Map<String, ClientInfo> getClientInfoMap() {
        return clientInfoMap;
    }

    /**
    * 是否Running
    *
    * @return 是否running的结果
     */
    public boolean isRunning() {
        return running;
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
        List<com.chua.common.support.network.sync.SyncMessageHandler> handlers = handlerMap.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        if (handlers.stream().noneMatch(h -> h == handler)) {
            handlers.add(handler);
        }
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
    * 设置同步服务端
    *
    * @param syncServer 同步服务端
     */
    public void setSyncServer(SyncServer syncServer) {
        this.syncServer = syncServer;
        /**
        * attach监听器。
         */
        attachListeners();
    }

    /** attach监听器 */
    private void attachListeners() {
        if (listenersAttached || syncServer == null) {
            return;
        }
        syncServer.addListener(new InternalServerListener());
        listenersAttached = true;
    }

    /** 开始 */
    public void start() {
        if (running) {
            log.warn("[SyncServer:{}] 已在运行中", instanceConfig.getName());
            return;
        }
        if (syncServer == null) {
            syncServer = createUnderlyingServer();
            /**
            * 设置同步服务端。
            * @param syncServer 同步服务端
             */
            setSyncServer(syncServer);
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

    /**
    * 创建underlying服务端
    *
    * @return 创建underlying服务端的结果
     */
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

    /** 停止 */
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

    /**
    * 发送
    *
    * @param sessionId 会话标识
    * @param topic topic
    * @param data 数据
     */
    public void send(String sessionId, String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        syncServer.send(sessionId, topic, SyncCryptoUtils.encrypt(data, syncProperties));
    }

    /**
    * Broadcast
    *
    * @param topic topic
    * @param data 数据
     */
    public void broadcast(String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        syncServer.publish(topic, SyncCryptoUtils.encrypt(data, syncProperties));
    }

    /**
    * broadcast转为others
    *
    * @param excludeClientId exclude客户端标识
    * @param topic topic
    * @param data 数据
     */
    public void broadcastToOthers(String excludeClientId, String topic, Object data) {
        if (syncServer == null || !running) {
            return;
        }
        List<String> clients = syncServer.getConnectedClients();
        Object outbound = SyncCryptoUtils.encrypt(data, syncProperties);
        for (String clientId : clients) {
            if (!Objects.equals(clientId, excludeClientId)) {
                syncServer.send(clientId, topic, outbound);
            }
        }
    }

    /**
    * 获取Connection计算数量
    *
    * @return 获取connection数量的结果
     */
    public int getConnectionCount() {
        return syncServer != null ? syncServer.getConnectedClients().size() : 0;
    }

    /**
    * 获取会话标识
    *
    * @return 获取会话标识的结果
     */
    public Set<String> getSessionIds() {
        return syncServer != null ? new HashSet<>(syncServer.getConnectedClients()) : new HashSet<>();
    }

    /**
    * contains客户端
    *
    * @param clientId 客户端标识
    * @return contains客户端的结果
     */
    public boolean containsClient(String clientId) {
        return syncServer != null && clientId != null && syncServer.getConnectedClients().contains(clientId);
    }

    /**
    * 内部服务端监听器类。
    *
    * @author CH
    * @since 4.0.0
     */
    private class InternalServerListener implements SyncServerListener {
        @Override
        /** on客户端连接 */
        public void onClientConnected(String clientId, java.util.Map<String, Object> metadata) {
            log.info("[SyncServer:{}] 客户端连接: clientId={}", instanceConfig.getName(), clientId);
            broadcastToOthers(clientId, TOPIC_CONNECT, Map.of(
                    "clientId", clientId, "event", "connect",
                    "onlineCount", getConnectionCount(), "timestamp", System.currentTimeMillis()));
        }

        @Override
        /** on客户端断开连接 */
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
        /** on消息 */
        public void onMessage(String clientId, String topic, Object message) {
            /**
            * 把手服务端消息。
            * @param clientId 客户端标识
            * @param topic topic
            * @param message 消息
             */
            handleServerMessage(clientId, topic, message);
        }

        @Override
        /** On记录错误 */
        public void onError(String clientId, Throwable cause) {
            log.error("[SyncServer:{}] 客户端错误: clientId={}", instanceConfig.getName(), clientId, cause);
        }
    }

    @SuppressWarnings("unchecked")
    /**
    * 处理服务端消息
    *
    * @param clientId 客户端标识
    * @param topic topic
    * @param data 数据
     */
    private void handleServerMessage(String clientId, String topic, Object data) {
        data = SyncCryptoUtils.decrypt(parseIfNeeded(data), syncProperties);
        log.info("[SyncServer:{}] 收到消息: clientId={}, topic={}, data={}",
                instanceConfig.getName(), clientId, topic, data);
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
        Map<String, Object> dataMap = new HashMap<>();
        if (data instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, value) -> dataMap.put(String.valueOf(key), value));
        } else {
            dataMap.put("data", data);
        }
        // 注入源客户端标识，供业务处理器反查会话上下文
        dataMap.put(SYNC_MESSAGE_SOURCE_CLIENT_ID, clientId);
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
    /**
    * 处理系统topic
    *
    * @param clientId 客户端标识
    * @param topic topic
    * @param data 数据
    * @return 把手系统topic的结果
     */
    private boolean handleSystemTopic(String clientId, String topic, Object data) {
        if (TOPIC_HEALTH.equals(topic)) {
            /**
            * 发送。
            * @param clientId 客户端标识
            * @param TOPIC_HEALTH topic健康
            * @param instanceConfig.getName( instance配置.获取名称(
             */
            send(clientId, TOPIC_HEALTH, Map.of("status", "UP", "instance", instanceConfig.getName(),
                    "connections", getConnectionCount(), "timestamp", System.currentTimeMillis()));
            return true;
        }
        if (isClientRegisterTopic(topic)) {
            /**
            * 把手客户端注册。
            * @param clientId 客户端标识
            * @param data 数据
             */
            handleClientRegister(clientId, data);
            return true;
        }
        if (isClientHeartbeatTopic(topic)) {
            /**
            * 把手客户端心跳。
            * @param clientId 客户端标识
             */
            handleClientHeartbeat(clientId);
            return true;
        }
        if (isClientOfflineTopic(topic)) {
            /**
            * 把手客户端offline。
            * @param clientId 客户端标识
             */
            handleClientOffline(clientId);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    /**
    * 处理客户端注册
    *
    * @param clientId 客户端标识
    * @param data 数据
     */
    private void handleClientRegister(String clientId, Object data) {
        try {
            ClientInfo clientInfo = new ClientInfo();
            if (data instanceof Map<?, ?> map) {
                /**
                * 副本映射转为客户端信息。
                * @param Object> 对象>
                 */
                copyMapToClientInfo((Map<String, Object>) map, clientInfo);
            }
            clientInfoMap.put(clientId, clientInfo);
            log.info("[SyncServer:{}] 客户端注册: clientId={}, app={}, ip={}:{}, metadata={}",
                    instanceConfig.getName(), clientId, clientInfo.getClientApplicationName(),
                    clientInfo.getClientIpAddress(), clientInfo.getClientPort(), clientInfo.getClientMetadata());
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

    /**
    * 复制映射转为客户端信息
    *
    * @param map 映射
    * @param clientInfo 客户端信息
     */
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
        if (map.containsKey("clientMetadata")) {
            Object metadata = map.get("clientMetadata");
            if (metadata instanceof Map<?, ?> rawMetadata) {
                Map<String, Object> converted = new HashMap<>();
                rawMetadata.forEach((key, value) -> converted.put(String.valueOf(key), value));
                clientInfo.setClientMetadata(converted);
            }
        }
    }

    /**
    * 处理客户端心跳
    *
    * @param clientId 客户端标识
     */
    private void handleClientHeartbeat(String clientId) {
        ClientInfo clientInfo = clientInfoMap.get(clientId);
        if (clientInfo != null) {
            clientInfo.setClientLastHeartbeatTime(System.currentTimeMillis());
            clientInfo.setClientOnline(true);
        }
    }

    /**
    * 处理客户端offline
    *
    * @param clientId 客户端标识
     */
    private void handleClientOffline(String clientId) {
        ClientInfo clientInfo = clientInfoMap.get(clientId);
        if (clientInfo != null) {
            clientInfo.setClientOnline(false);
        }
    }

    /**
    * 是否客户端注册Topic
    *
    * @param topic topic
    * @return 是否客户端注册topic的结果
     */
    private boolean isClientRegisterTopic(String topic) {
        return matchesClientTopic(topic, syncProperties.getClient().getRegisterTopic(), TOPIC_CLIENT_REGISTER);
    }

    /**
    * 是否客户端心跳topic
    *
    * @param topic topic
    * @return 是否客户端心跳topic的结果
     */
    private boolean isClientHeartbeatTopic(String topic) {
        return matchesClientTopic(topic, syncProperties.getClient().getHeartbeatTopic(), TOPIC_CLIENT_HEARTBEAT);
    }

    /**
    * 是否客户端offlinetopic
    *
    * @param topic topic
    * @return 是否客户端offlinetopic的结果
     */
    private boolean isClientOfflineTopic(String topic) {
        return matchesClientTopic(topic, syncProperties.getClient().getOfflineTopic(), TOPIC_CLIENT_OFFLINE);
    }

    /**
    * 匹配客户端topic
    *
    * @param actualTopic actualtopic
    * @param configuredTopic configuredtopic
    * @param defaultTopic 默认topic
    * @return 匹配客户端topic的结果
     */
    private boolean matchesClientTopic(String actualTopic, String configuredTopic, String defaultTopic) {
        if (actualTopic == null || actualTopic.isBlank()) {
            return false;
        }
        if (Objects.equals(actualTopic, configuredTopic)) {
            return true;
        }
        return Objects.equals(actualTopic, defaultTopic);
    }

    /**
    * 将传输层收到的字符串消息解析为结构化对象（JSON -> 映射）。
    * <p>
    * 底层协议（如 web套接字）按文本帧传输，消息体为 JSON 字符串，
    * 解析后方可与加密解密逻辑统一处理。
    * </p>
    *
    * @param message 收到的消息
    * @return 解析后的对象，无法解析时原样返回
     */
    private Object parseIfNeeded(Object message) {
        if (!(message instanceof String text) || text.isBlank()) {
            return message;
        }
        try {
            return Json.fromJson(text, Map.class);
        } catch (Exception e) {
            return message;
        }
    }
}
