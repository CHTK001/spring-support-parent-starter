package com.chua.tenant.support.sync.client;

import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.client.ListenerManager;
import com.chua.common.support.protocol.sync.SyncProtocol;
import com.chua.common.support.protocol.sync.SyncProtocolClient;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.tenant.support.properties.TenantProperties;
import com.chua.tenant.support.sync.TenantMetadataConsumer;
import com.chua.tenant.support.sync.server.TenantSyncServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 租户同步客户端
 * <p>
 * 基于 SyncProtocol 实现长连接通信，支持：
 * <ul>
 *   <li>实时接收服务端推送的元数据</li>
 *   <li>自动重连机制</li>
 *   <li>心跳保活</li>
 *   <li>多协议支持（通过配置切换）</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/02
 * @since 2024/12/03 重构为 SyncProtocol 实现
 */
@Slf4j
public class TenantSyncClient implements InitializingBean, DisposableBean {

    private final TenantProperties tenantProperties;
    
    /**
     * 同步协议客户端
     */
    @Getter
    private SyncProtocolClient protocolClient;
    
    /**
     * 元数据同步调度器
     */
    private ScheduledExecutorService scheduler;
    
    /**
     * 元数据消费者列表
     */
    private final List<TenantMetadataConsumer> consumers = new ArrayList<>();
    
    /**
     * 连接状态
     */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    /**
     * 重连次数
     */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public TenantSyncClient(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
        loadConsumers();
    }

    /**
     * 加载所有元数据消费者
     */
    private void loadConsumers() {
        ServiceProvider<TenantMetadataConsumer> serviceProvider = ServiceProvider.of(TenantMetadataConsumer.class);
        consumers.addAll(serviceProvider.collect());
        consumers.sort(Comparator.comparingInt(TenantMetadataConsumer::getOrder));
        log.info("[租户同步] 加载了 {} 个元数据消费者", consumers.size());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        TenantProperties.SyncProtocol syncProtocol = tenantProperties.getSyncProtocol();
        if (!syncProtocol.isEnable() || !"client".equalsIgnoreCase(syncProtocol.getType())) {
            log.info("[租户同步] 客户端未启用");
            return;
        }

        connect();
        startMetadataSyncScheduler();
    }

    /**
     * 连接到服务端
     */
    private void connect() {
        try {
            TenantProperties.SyncProtocol config = tenantProperties.getSyncProtocol();
            
            // 解析服务端地址
            URI serverUri = URI.create(config.getServerAddress());
            String host = serverUri.getHost();
            int port = serverUri.getPort() > 0 ? serverUri.getPort() : config.getServerPort();
            
            // 构建协议配置
            ProtocolSetting protocolSetting = ProtocolSetting.builder()
                    .protocol(config.getProtocol())
                    .host(host)
                    .port(port)
                    .heartbeat(config.isHeartbeat())
                    .heartbeatInterval(config.getHeartbeatInterval())
                    .connectTimeoutMillis(config.getConnectTimeout())
                    .build();
            
            // 创建同步协议实例
            SyncProtocol protocol = SyncProtocol.create(config.getProtocol(), protocolSetting);
            
            // 创建客户端
            protocolClient = protocol.createClient(protocolSetting);
            
            // 订阅主题
            protocolClient.subscribe(
                    TenantSyncServer.TOPIC_METADATA,
                    TenantSyncServer.TOPIC_SYNC_RESPONSE,
                    TenantSyncServer.TOPIC_HEALTH
            );
            
            // 添加消息监听器
            ListenerManager listenerManager = protocolClient.getListenerManager();
            listenerManager.addMessageListener(this::onMessage);
            listenerManager.addConnectionListener(this::onConnectionStateChanged);
            
            // 连接服务端
            protocolClient.connect();
            connected.set(true);
            reconnectAttempts.set(0);
            
            log.info("[租户同步] 客户端连接成功，协议: {}，服务器: {}:{}", 
                    config.getProtocol(), host, port);
        } catch (Exception e) {
            log.error("[租户同步] 客户端连接失败", e);
            connected.set(false);
            scheduleReconnect();
        }
    }
    
    /**
     * 处理连接状态变化
     *
     * @param isConnected 是否连接
     */
    private void onConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            connected.set(true);
            reconnectAttempts.set(0);
            log.info("[租户同步] 客户端已连接");
            
            // 连接成功后立即请求同步
            requestSync();
        } else {
            connected.set(false);
            log.warn("[租户同步] 客户端已断开");
            scheduleReconnect();
        }
    }
    
    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        TenantProperties.SyncProtocol config = tenantProperties.getSyncProtocol();
        int maxAttempts = config.getMaxReconnectAttempts();
        
        if (maxAttempts >= 0 && reconnectAttempts.get() >= maxAttempts) {
            log.error("[租户同步] 达到最大重连次数 {}，停止重连", maxAttempts);
            return;
        }
        
        int attempts = reconnectAttempts.incrementAndGet();
        int delay = config.getReconnectInterval();
        
        log.info("[租户同步] {} 秒后尝试第 {} 次重连", delay, attempts);
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 处理接收到的消息
     *
     * @param topic 主题
     * @param data 数据
     */
    @SuppressWarnings("unchecked")
    private void onMessage(String topic, Object data) {
        log.debug("[租户同步] 收到消息: topic={}", topic);
        
        try {
            Map<String, Object> message = (Map<String, Object>) data;
            
            switch (topic) {
                case TenantSyncServer.TOPIC_METADATA -> handleMetadataPush(message);
                case TenantSyncServer.TOPIC_SYNC_RESPONSE -> handleSyncResponse(message);
                case TenantSyncServer.TOPIC_HEALTH -> handleHealthResponse(message);
                default -> log.warn("[租户同步] 未知主题: {}", topic);
            }
        } catch (Exception e) {
            log.error("[租户同步] 处理消息失败: topic={}", topic, e);
        }
    }
    
    /**
     * 处理服务端推送的元数据
     *
     * @param message 消息
     */
    @SuppressWarnings("unchecked")
    private void handleMetadataPush(Map<String, Object> message) {
        String tenantId = (String) message.get("tenantId");
        Map<String, Object> metadata = (Map<String, Object>) message.get("data");
        
        if (tenantId != null && metadata != null && !metadata.isEmpty()) {
            processMetadata(tenantId, metadata);
            log.info("[租户同步] 收到租户 {} 元数据推送，共 {} 项", tenantId, metadata.size());
        }
    }
    
    /**
     * 处理同步响应
     *
     * @param message 消息
     */
    @SuppressWarnings("unchecked")
    private void handleSyncResponse(Map<String, Object> message) {
        Integer code = (Integer) message.get("code");
        
        if (code != null && code == 200) {
            Map<String, Object> metadata = (Map<String, Object>) message.get("data");
            String tenantId = getCurrentTenantId();
            
            if (tenantId != null && metadata != null && !metadata.isEmpty()) {
                processMetadata(tenantId, metadata);
                log.info("[租户同步] 租户 {} 元数据同步成功，共 {} 项", tenantId, metadata.size());
            }
        } else {
            log.warn("[租户同步] 同步失败: {}", message.get("message"));
        }
    }
    
    /**
     * 处理健康检查响应
     *
     * @param message 消息
     */
    private void handleHealthResponse(Map<String, Object> message) {
        log.debug("[租户同步] 服务端健康状态: {}", message.get("status"));
    }
    
    /**
     * 请求同步元数据
     */
    public void requestSync() {
        String tenantId = getCurrentTenantId();
        if (tenantId == null) {
            log.warn("[租户同步] 无法获取当前租户ID，跳过同步");
            return;
        }
        
        if (!connected.get() || protocolClient == null) {
            log.warn("[租户同步] 未连接服务端，跳过同步");
            return;
        }
        
        Map<String, Object> request = Map.of("tenantId", tenantId);
        protocolClient.publish(TenantSyncServer.TOPIC_SYNC_REQUEST, request);
        log.debug("[租户同步] 已发送同步请求: tenantId={}", tenantId);
    }

    /**
     * 启动元数据同步调度器
     */
    private void startMetadataSyncScheduler() {
        TenantProperties.SyncProtocol.MetadataSync metadataSync = tenantProperties.getSyncProtocol().getMetadataSync();

        if (!metadataSync.isEnable()) {
            log.info("[租户同步] 元数据同步未启用");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "tenant-metadata-sync-client");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
                this::requestSync,
                metadataSync.getInitialDelay(),
                metadataSync.getInterval(),
                TimeUnit.SECONDS);

        log.info("[租户同步] 元数据同步调度器启动成功，间隔: {}秒", metadataSync.getInterval());
    }

    /**
     * 处理元数据
     *
     * @param tenantId 租户ID
     * @param metadata 元数据
     */
    private void processMetadata(String tenantId, Map<String, Object> metadata) {
        for (TenantMetadataConsumer consumer : consumers) {
            try {
                consumer.consumeMetadata(tenantId, metadata);
                log.debug("[租户同步] 消费者 {} 处理租户 {} 元数据完成",
                        consumer.getName(), tenantId);
            } catch (Exception e) {
                log.error("[租户同步] 消费者 {} 处理租户 {} 元数据失败",
                        consumer.getName(), tenantId, e);
            }
        }
    }

    /**
     * 获取当前租户ID
     * 子类可以重写此方法从上下文或配置中获取
     *
     * @return 租户ID
     */
    protected String getCurrentTenantId() {
        // 默认返回null，子类应该重写此方法
        // 可以从 RequestUtils.getTenantId() 或配置中获取
        return null;
    }

    /**
     * 手动触发同步
     *
     * @param tenantId 租户ID
     */
    public void syncNow(String tenantId) {
        if (!connected.get() || protocolClient == null) {
            log.warn("[租户同步] 未连接服务端，无法同步");
            return;
        }
        
        Map<String, Object> request = Map.of("tenantId", tenantId);
        protocolClient.publish(TenantSyncServer.TOPIC_SYNC_REQUEST, request);
        log.info("[租户同步] 已发送手动同步请求: tenantId={}", tenantId);
    }
    
    /**
     * 发送健康检查请求
     */
    public void healthCheck() {
        if (!connected.get() || protocolClient == null) {
            log.warn("[租户同步] 未连接服务端，无法进行健康检查");
            return;
        }
        
        protocolClient.publish(TenantSyncServer.TOPIC_HEALTH, Map.of("action", "ping"));
    }
    
    /**
     * 检查是否已连接
     *
     * @return 是否连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void destroy() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[租户同步] 元数据同步调度器已关闭");
        }
        
        if (protocolClient != null) {
            protocolClient.close();
            log.info("[租户同步] 客户端已关闭");
        }
        
        connected.set(false);
    }
}
