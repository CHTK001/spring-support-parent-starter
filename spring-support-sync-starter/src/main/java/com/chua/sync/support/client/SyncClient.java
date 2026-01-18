package com.chua.sync.support.client;

import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.common.support.network.protocol.ProtocolSetting;
import com.chua.common.support.network.protocol.ServerSetting;
import com.chua.common.support.network.protocol.listener.ConnectionEvent;
import com.chua.common.support.network.protocol.listener.ConnectionListener;
import com.chua.common.support.network.protocol.listener.DataEvent;
import com.chua.common.support.network.protocol.listener.TopicListener;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.sync.support.pojo.ClientInfo;
import com.chua.sync.support.pojo.SyncResponse;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.server.SyncServer;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 同步协议客户端
 * <p>
 * 基于 SyncProtocol 实现长连接通信，支持：
 * <ul>
 *   <li>订阅配置的所有主题</li>
 *   <li>通过 SPI 加载处理器处理消息</li>
 *   <li>自动重连机制</li>
 *   <li>心跳保活</li>
 *   <li>同步请求/响应</li>
 *   <li>客户端信息注册</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Slf4j
public class SyncClient implements InitializingBean, DisposableBean {

    @Getter
    private final SyncProperties syncProperties;

    /**
     * Spring 环境配置
     */
    private final Environment environment;

    /**
     * 同步客户端
     */
    @Getter
    private com.chua.common.support.network.protocol.sync.SyncClient syncClient;

    /**
     * 定时同步调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 消息处理器映射：topic -> handlers
     */
    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>();

    /**
     * 连接状态
     */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 客户端信息
     */
    @Getter
    private ClientInfo clientInfo;

    /**
     * 请求响应映射(用于同步请求)
     */
    private final Map<String, CompletableFuture<SyncResponse>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 连接监听器列表
     */
    private final List<Consumer<Boolean>> connectionListeners = new CopyOnWriteArrayList<>();

    /**
     * 动态订阅的主题
     */
    private final Set<String> dynamicTopics = ConcurrentHashMap.newKeySet();

    /**
     * 构造方法
     *
     * @param syncProperties 同步配置属性
     * @param environment    Spring 环境配置
     * @author CH
     * @since 1.0.0
     */
    public SyncClient(SyncProperties syncProperties, Environment environment) {
        this.syncProperties = syncProperties;
        this.environment = environment;
        loadHandlers();
    }

    /**
     * 获取客户端ID
     */
    public String getClientId() {
        return syncClient != null ? syncClient.getClientId() : null;
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
                log.info("[Sync客户端] 主题 {} 加载了 {} 个处理器", topic, handlers.size());
            }
        }

        log.info("[Sync客户端] 共加载 {} 个主题的处理器", handlerMap.size());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!syncProperties.isClientEnabled()) {
            log.info("[Sync客户端] 未启用");
            return;
        }

        initClientInfo();
        connect();
        startScheduler();
    }

    /**
     * 初始化客户端信息
     * <p>
     * 从 Spring Environment 获取 appName、contextPath、actuatorPath
     * </p>
     *
     * @author CH
     * @since 1.0.0
     */
    private void initClientInfo() {
        try {
            SyncProperties.ClientConfig clientConfig = syncProperties.getClient();
            InetAddress localHost = InetAddress.getLocalHost();
            Runtime runtime = Runtime.getRuntime();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

            String instanceId = clientConfig.getInstanceId();
            if (instanceId == null || instanceId.isEmpty()) {
                instanceId = UUID.randomUUID().toString().substring(0, 8);
            }

            // 多网卡场景：优先使用配置的 IP 地址
            String ipAddress = clientConfig.getIpAddress();
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = localHost.getHostAddress();
            }
            
            // 从 Spring Environment 获取端口
            String serverPort = environment.getProperty("server.port", "8080");
            int port = Integer.parseInt(serverPort);
            
            // 从 Spring Environment 获取 contextPath
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            
            // 从 Spring Environment 获取应用名称
            String appName = environment.getProperty("spring.application.name", "");
            
            // 从 Spring Environment 获取激活的环境（profiles）
            String applicationActive = environment.getProperty("spring.profiles.active", "default");
            
            // 从 Spring Environment 获取 actuatorPath
            String actuatorPath = environment.getProperty("management.endpoints.web.base-path", "/actuator");
            
            // 构建服务地址
            String serviceUrl = String.format("http://%s:%d%s", ipAddress, port,
                    contextPath != null && !contextPath.isEmpty() ? contextPath : "");

            // 构建扩展元数据，存储详细的系统信息
            Map<String, Object> metadata = clientConfig.getMetadata() != null 
                    ? new java.util.HashMap<>(clientConfig.getMetadata()) 
                    : new java.util.HashMap<>();
            metadata.put("osVersion", System.getProperty("os.version"));
            metadata.put("osArch", System.getProperty("os.arch"));
            metadata.put("jvmName", runtimeBean.getVmName());
            metadata.put("jvmVersion", runtimeBean.getVmVersion());
            metadata.put("cpuCores", runtime.availableProcessors());
            metadata.put("totalMemory", runtime.maxMemory());
            metadata.put("heapUsed", runtime.totalMemory() - runtime.freeMemory());
            metadata.put("heapMax", runtime.maxMemory());
            metadata.put("threadCount", Thread.activeCount());
            
            clientInfo = ClientInfo.builder()
                    // 应用基本信息（从 Spring 获取）
                    .clientApplicationName(appName)
                    .clientApplicationActive(applicationActive)
                    .clientInstanceId(instanceId)
                    .clientContextPath(contextPath)
                    .clientUrl(serviceUrl)
                    .clientActuatorPath(actuatorPath)
                    // 网络信息
                    .clientIpAddress(ipAddress)
                    .clientPort(port)
                    .clientHostname(localHost.getHostName())
                    // 系统信息（核心）
                    .clientOsName(System.getProperty("os.name"))
                    .clientJavaVersion(System.getProperty("java.version"))
                    .clientPid(runtimeBean.getPid())
                    // 时间信息
                    .clientStartTime(System.currentTimeMillis())
                    .clientOnline(true)
                    // 扩展信息
                    .clientMetadata(metadata)
                    .clientCapabilities(clientConfig.getCapabilities())
                    .build();

            log.info("[Sync客户端] 客户端信息初始化: app={}, active={}, ip={}, port={}, clientUrl={}, actuatorPath={}",
                    clientInfo.getClientApplicationName(), clientInfo.getClientApplicationActive(), 
                    clientInfo.getClientIpAddress(), clientInfo.getClientPort(), serviceUrl, actuatorPath);
        } catch (Exception e) {
            log.error("[Sync客户端] 初始化客户端信息失败", e);
            clientInfo = ClientInfo.builder().clientStartTime(System.currentTimeMillis()).clientOnline(true).build();
        }
    }

    /**
     * 连接到服务端
     */
    private void connect() {
        try {

            SyncProperties.ClientConfig clientConfig = syncProperties.getClient();
            // 解析服务端地址

            String host = clientConfig.getServerHost();
            int port = clientConfig.getServerPort();
            
            // 如果配置了完整地址，解析它
            if (clientConfig.getServerAddress() != null && !clientConfig.getServerAddress().isEmpty()) {
                try {
                    URI serverUri = URI.create(clientConfig.getServerAddress());
                    if (serverUri.getHost() != null) {
                        host = serverUri.getHost();
                    }
                    if (serverUri.getPort() > 0) {
                        port = serverUri.getPort();
                    }
                } catch (Exception ignored) {
                }
            }

            String protocol = clientConfig.getProtocol();

            // 构建协议配置（包含重连参数）
            ClientSetting protocolSetting = ClientSetting.builder()
                    .protocol(protocol)
                    .host(host)
                    .port(port)
                    .connectTimeoutMillis(10000)          // 连接超时10秒
                    .autoReconnectEnabled(true)           // 启用自动重连
                    .maxRetries(-1)                       // 无限重连（-1表示无限）
                    .retryIntervalMillis(5000)            // 重连间隔5秒（不自动断开空闲连接）
                    .build();

            // 使用新的 SyncClient 接口创建客户端
            syncClient = com.chua.common.support.network.protocol.sync.SyncClient.create(protocol, protocolSetting);

            // 订阅所有配置的主题 + 系统主题
            List<String> allTopics = new ArrayList<>(syncProperties.getTopics().keySet());
            allTopics.add(SyncServer.TOPIC_HEALTH);
            allTopics.add(SyncServer.TOPIC_RESPONSE);
            allTopics.add(SyncServer.TOPIC_CONNECT);      // 监听其他客户端连接事件
            allTopics.add(SyncServer.TOPIC_DISCONNECT);   // 监听其他客户端断开事件
            syncClient.subscribe(allTopics.toArray(new String[0]));

            // 为每个主题添加监听器
            for (String topic : allTopics) {
                syncClient.getListenerManager().addTopicListener(new TopicListener() {
                    @Override
                    public String getTopic() {
                        return topic;
                    }

                    @Override
                    public void onEvent(DataEvent event) {
                        SyncClient.this.onMessage(event.getTopic(), event.getData());
                    }
                });
            }

            // 添加连接监听器
            syncClient.getListenerManager().addConnectionListener(new ConnectionListener() {
                @Override
                public void onEvent(ConnectionEvent event) {
                    boolean isConnected = event.getEventType() == ConnectionEvent.Type.CONNECTED ||
                            event.getEventType() == ConnectionEvent.Type.RECONNECTED;
                    onConnectionStateChanged(isConnected);
                }
            });

            // 连接服务端
            syncClient.connect();
            clientInfo.setClientId(syncClient.getClientId());
            connected.set(true);

            // 自动注册
            if (syncProperties.getClient().isAutoRegister()) {
                register();
            }

            // 通知连接监听器
            notifyConnectionListeners(true);

            log.info("[Sync客户端] 连接成功，协议: {}，服务器: {}:{}, clientId={}",
                    syncProperties.getClient().getProtocol(), host, port, syncClient.getClientId());
        } catch (Exception e) {
            log.error("[Sync客户端] 连接失败，底层将自动重连", e);
            connected.set(false);
        }
    }

    /**
     * 发送注册消息
     */
    public void register() {
        if (!connected.get()) return;
        clientInfo.setClientRegisterTime(System.currentTimeMillis());
        publish(syncProperties.getClient().getRegisterTopic(), clientInfo);
        log.info("[Sync客户端] 发送注册消息: clientId={}", clientInfo.getClientId());
    }

    /**
     * 发送心跳
     */
    public void sendHeartbeat() {
        if (!connected.get()) return;
        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("clientId", getClientId());
        heartbeat.put("timestamp", System.currentTimeMillis());
        heartbeat.put("online", true);
        publish(syncProperties.getClient().getHeartbeatTopic(), heartbeat);
        clientInfo.setClientLastHeartbeatTime(System.currentTimeMillis());
    }

    /**
     * 添加连接监听器
     */
    public void addConnectionListener(Consumer<Boolean> listener) {
        connectionListeners.add(listener);
    }

    /**
     * 通知连接监听器
     */
    private void notifyConnectionListeners(boolean isConnected) {
        for (Consumer<Boolean> listener : connectionListeners) {
            try {
                listener.accept(isConnected);
            } catch (Exception e) {
                log.error("[Sync客户端] 连接监听器执行失败", e);
            }
        }
    }

    /**
     * 动态订阅主题
     */
    public void subscribe(String... topics) {
        if (syncClient == null) return;
        syncClient.subscribe(topics);
        dynamicTopics.addAll(Arrays.asList(topics));
        
        // 为新主题添加监听器
        for (String topic : topics) {
            syncClient.getListenerManager().addTopicListener(new TopicListener() {
                @Override
                public String getTopic() {
                    return topic;
                }

                @Override
                public void onEvent(DataEvent event) {
                    SyncClient.this.onMessage(event.getTopic(), event.getData());
                }
            });
        }
        log.info("[Sync客户端] 动态订阅主题: {}", Arrays.toString(topics));
    }

    /**
     * 动态注册消息处理器
     */
    public void registerHandler(String topic, SyncMessageHandler handler) {
        handlerMap.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
        // 如果主题未订阅，自动订阅
        if (!dynamicTopics.contains(topic) && !syncProperties.getTopics().containsKey(topic)) {
            subscribe(topic);
        }
        log.info("[Sync客户端] 注册处理器: topic={}, handler={}", topic, handler.getName());
    }

    /**
     * 同步请求(等待响应)
     *
     * @param topic     主题
     * @param data      数据
     * @param timeoutMs 超时时间(毫秒)
     * @return 响应
     */
    public SyncResponse request(String topic, Object data, long timeoutMs) {
        if (!connected.get()) {
            return SyncResponse.error(null, getClientId(), "未连接服务端");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<SyncResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("requestId", requestId);
            requestData.put("clientId", getClientId());
            requestData.put("data", data);
            requestData.put("timestamp", System.currentTimeMillis());

            publish(topic, requestData);

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return SyncResponse.error(requestId, getClientId(), "请求超时");
        } catch (Exception e) {
            return SyncResponse.error(requestId, getClientId(), e.getMessage());
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * 同步请求(默认30秒超时)
     */
    public SyncResponse request(String topic, Object data) {
        return request(topic, data, 30000);
    }

    /**
     * 处理连接状态变化
     * <p>
     * 注：断线重连由底层 SyncProtocolClient 自动处理
     */
    private void onConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            connected.set(true);
            // 重连后自动注册
            if (syncProperties.getClient().isAutoRegister()) {
                register();
            }
            notifyConnectionListeners(true);
            log.info("[Sync客户端] 已连接");
        } else {
            connected.set(false);
            notifyConnectionListeners(false);
            log.warn("[Sync客户端] 已断开，底层将自动重连");
        }
    }

    /**
     * 处理接收到的消息
     */
    @SuppressWarnings("unchecked")
    private void onMessage(String topic, Object data) {
        log.debug("[Sync客户端] 收到消息: topic={}", topic);

        try {
            // 处理系统主题
            if (SyncServer.TOPIC_HEALTH.equals(topic)) {
                log.debug("[Sync客户端] 服务端健康状态: {}", data);
                return;
            }

            if (SyncServer.TOPIC_RESPONSE.equals(topic)) {
                handleResponse(data);
                return;
            }

            // 处理连接/断开事件
            if (SyncServer.TOPIC_CONNECT.equals(topic)) {
                Map<String, Object> event = (Map<String, Object>) data;
                log.info("[Sync客户端] 其他客户端连接: sessionId={}, 当前在线: {}",
                        event.get("sessionId"), event.get("onlineCount"));
                handleConnectionEvent(topic, event);
                return;
            }

            if (SyncServer.TOPIC_DISCONNECT.equals(topic)) {
                Map<String, Object> event = (Map<String, Object>) data;
                log.info("[Sync客户端] 其他客户端断开: sessionId={}, 当前在线: {}",
                        event.get("sessionId"), event.get("onlineCount"));
                handleConnectionEvent(topic, event);
                return;
            }

            // 获取主题对应的处理器
            List<SyncMessageHandler> handlers = handlerMap.get(topic);
            if (handlers == null || handlers.isEmpty()) {
                log.warn("[Sync客户端] 未找到主题 {} 的处理器", topic);
                return;
            }

            // 循环执行所有处理器
            Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : Map.of("data", data);
            for (SyncMessageHandler handler : handlers) {
                try {
                    handler.handle(topic, null, dataMap);
                    log.debug("[Sync客户端] 处理器 {} 处理主题 {} 完成", handler.getName(), topic);
                } catch (Exception e) {
                    log.error("[Sync客户端] 处理器 {} 处理主题 {} 失败", handler.getName(), topic, e);
                }
            }
        } catch (Exception e) {
            log.error("[Sync客户端] 处理消息失败: topic={}", topic, e);
        }
    }

    /**
     * 发布消息到服务端
     *
     * @param topic 主题
     * @param data  数据
     */
    public void publish(String topic, Object data) {
        if (!connected.get() || syncClient == null) {
            log.warn("[Sync客户端] 未连接服务端，无法发布消息");
            return;
        }
        syncClient.publish(topic, data);
        log.debug("[Sync客户端] 已发布消息: topic={}", topic);
    }

    /**
     * 发送健康检查请求
     */
    public void healthCheck() {
        if (!connected.get() || syncClient == null) {
            log.warn("[Sync客户端] 未连接服务端，无法进行健康检查");
            return;
        }
        syncClient.publish(SyncServer.TOPIC_HEALTH, Map.of("action", "ping"));
    }

    /**
     * 启动定时同步调度器
     *
     * @author CH
     * @since 1.0.0
     */
    private void startScheduler() {
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "sync-client-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        // 心跳任务
        int heartbeatInterval = syncProperties.getClient().getHeartbeatInterval();
        if (heartbeatInterval > 0) {
            scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    heartbeatInterval,
                    heartbeatInterval,
                    TimeUnit.SECONDS);
        }

        // 连接状态同步任务（同步底层连接状态）
        scheduler.scheduleAtFixedRate(
                this::syncConnectionStatus,
                10,
                10,
                TimeUnit.SECONDS);

        log.info("[Sync客户端] 调度器启动，心跳间隔: {}秒", heartbeatInterval);
    }

    /**
     * 同步连接状态
     * <p>
     * 定时同步底层连接状态到上层，底层已自动处理重连
     */
    private void syncConnectionStatus() {
        try {
            if (syncClient == null) {
                if (connected.get()) {
                    connected.set(false);
                    notifyConnectionListeners(false);
                }
                return;
            }

            // 同步底层连接状态
            boolean actualConnected = syncClient.isConnected();
            if (connected.get() != actualConnected) {
                connected.set(actualConnected);
                notifyConnectionListeners(actualConnected);
                if (actualConnected) {
                    log.info("[Sync客户端] 检测到连接已恢复");
                    // 重连后自动注册
                    if (syncProperties.getClient().isAutoRegister()) {
                        register();
                    }
                } else {
                    log.warn("[Sync客户端] 检测到连接已断开");
                }
            }
        } catch (Exception e) {
            log.debug("[Sync客户端] 连接状态检测异常: {}", e.getMessage());
        }
    }

    /**
     * 处理响应消息
     * <p>
     * 用于同步请求的响应处理，根据 requestId 匹配等待中的请求
     * </p>
     *
     * @param data 响应数据
     */
    @SuppressWarnings("unchecked")
    private void handleResponse(Object data) {
        try {
            if (!(data instanceof Map)) {
                log.warn("[Sync客户端] 响应数据格式错误: {}", data);
                return;
            }

            Map<String, Object> responseData = (Map<String, Object>) data;
            String requestId = (String) responseData.get("requestId");
            if (requestId == null) {
                log.debug("[Sync客户端] 响应无 requestId，忽略");
                return;
            }

            CompletableFuture<SyncResponse> future = pendingRequests.get(requestId);
            if (future == null) {
                log.debug("[Sync客户端] 未找到等待的请求: requestId={}", requestId);
                return;
            }

            // 构建响应对象
            boolean success = Boolean.TRUE.equals(responseData.get("success"));
            String message = (String) responseData.get("message");
            Object result = responseData.get("data");

            SyncResponse response = success
                    ? SyncResponse.success(requestId, getClientId(), result)
                    : SyncResponse.error(requestId, getClientId(), message);

            future.complete(response);
            log.debug("[Sync客户端] 响应已处理: requestId={}, success={}", requestId, success);
        } catch (Exception e) {
            log.error("[Sync客户端] 处理响应失败", e);
        }
    }

    /**
     * 处理连接/断开事件
     * <p>
     * 会查找配置中 topics 映射的 "sync/connect" 或 "sync/disconnect" 处理器执行
     * </p>
     *
     * @param topic 主题（sync/connect 或 sync/disconnect）
     * @param event 事件数据
     */
    private void handleConnectionEvent(String topic, Map<String, Object> event) {
        // 查找该主题对应的处理器
        List<SyncMessageHandler> handlers = handlerMap.get(topic);
        if (handlers == null || handlers.isEmpty()) {
            // 没有配置处理器，仅记录日志
            return;
        }

        // 循环执行所有处理器
        for (SyncMessageHandler handler : handlers) {
            try {
                handler.handle(topic, null, event);
                log.debug("[Sync客户端] 处理器 {} 处理 {} 事件完成", handler.getName(), topic);
            } catch (Exception e) {
                log.error("[Sync客户端] 处理器 {} 处理 {} 事件失败", handler.getName(), topic, e);
            }
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void destroy() throws Exception {
        log.info("[Sync客户端] 正在停止...");

        // 发送下线通知
        if (connected.get() && syncClient != null) {
            try {
                publish(syncProperties.getClient().getOfflineTopic(), Map.of(
                        "clientId", getClientId(),
                        "timestamp", System.currentTimeMillis()
                ));
            } catch (Exception ignored) {
            }
        }

        // 关闭定时同步调度器
        if (scheduler != null) {
            scheduler.shutdown();
        }

        if (syncClient != null) {
            syncClient.close();
        }

        connected.set(false);
        log.info("[Sync客户端] 已停止");
    }
}
