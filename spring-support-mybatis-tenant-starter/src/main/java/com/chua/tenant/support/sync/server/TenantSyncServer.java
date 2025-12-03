package com.chua.tenant.support.sync.server;

import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.sync.SyncConnectionListener;
import com.chua.common.support.protocol.sync.SyncMessageListener;
import com.chua.common.support.protocol.sync.SyncProtocol;
import com.chua.common.support.protocol.sync.SyncProtocolServer;
import com.chua.common.support.protocol.sync.SyncSession;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.tenant.support.properties.TenantProperties;
import com.chua.tenant.support.sync.TenantMetadataProvider;
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
 * 租户同步服务端
 * <p>
 * 基于 SyncProtocol 实现长连接通信，支持：
 * <ul>
 *   <li>实时元数据下发</li>
 *   <li>客户端连接管理</li>
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
public class TenantSyncServer implements InitializingBean, DisposableBean {

    /**
     * 主题常量
     */
    public static final String TOPIC_METADATA = "tenant/metadata";
    public static final String TOPIC_SYNC_REQUEST = "tenant/sync/request";
    public static final String TOPIC_SYNC_RESPONSE = "tenant/sync/response";
    public static final String TOPIC_HEALTH = "tenant/health";

    private final TenantProperties tenantProperties;
    
    /**
     * 同步协议服务端
     */
    @Getter
    private SyncProtocolServer protocolServer;
    
    /**
     * 元数据同步调度器
     */
    private ScheduledExecutorService scheduler;
    
    /**
     * 元数据提供者列表
     */
    private final List<TenantMetadataProvider> providers = new ArrayList<>();
    
    /**
     * 客户端会话与租户ID映射
     */
    private final Map<String, String> sessionTenantMap = new ConcurrentHashMap<>();

    public TenantSyncServer(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
        loadProviders();
    }

    /**
     * 加载所有元数据提供者
     */
    private void loadProviders() {
        ServiceProvider<TenantMetadataProvider> serviceProvider = ServiceProvider.of(TenantMetadataProvider.class);
        providers.addAll(serviceProvider.collect());
        providers.sort(Comparator.comparingInt(TenantMetadataProvider::getOrder));
        log.info("[租户同步] 加载了 {} 个元数据提供者", providers.size());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        TenantProperties.SyncProtocol syncProtocol = tenantProperties.getSyncProtocol();
        if (!syncProtocol.isEnable() || !"server".equalsIgnoreCase(syncProtocol.getType())) {
            log.info("[租户同步] 服务端未启用");
            return;
        }

        startServer();
        startMetadataSyncScheduler();
    }

    /**
     * 启动同步协议服务器
     * 使用 SyncProtocol 实现长连接通信
     */
    private void startServer() {
        try {
            TenantProperties.SyncProtocol config = tenantProperties.getSyncProtocol();
            
            // 构建协议配置
            ProtocolSetting protocolSetting = ProtocolSetting.builder()
                    .protocol(config.getProtocol())
                    .host(config.getServerHost())
                    .port(config.getServerPort())
                    .heartbeat(config.isHeartbeat())
                    .heartbeatInterval(config.getHeartbeatInterval())
                    .connectTimeoutMillis(config.getConnectTimeout())
                    .build();
            
            // 创建同步协议实例
            SyncProtocol protocol = SyncProtocol.create(config.getProtocol(), protocolSetting);
            
            // 创建服务端
            protocolServer = protocol.createServer(protocolSetting);
            
            // 添加连接监听器
            protocolServer.addConnectionListener(new TenantConnectionListener());
            
            // 添加消息监听器
            protocolServer.addMessageListener(new TenantMessageListener());
            
            // 启动服务器
            protocolServer.start();
            
            log.info("[租户同步] 服务端启动成功，协议: {}，地址: {}:{}", 
                    config.getProtocol(), config.getServerHost(), config.getServerPort());
        } catch (Exception e) {
            log.error("[租户同步] 服务端启动失败", e);
        }
    }
    
    /**
     * 连接监听器
     */
    private class TenantConnectionListener implements SyncConnectionListener {
        
        @Override
        public void onConnect(SyncSession session) {
            log.info("[租户同步] 客户端连接: sessionId={}", session.getId());
        }
        
        @Override
        public void onDisconnect(SyncSession session) {
            String sessionId = session.getId();
            String tenantId = sessionTenantMap.remove(sessionId);
            log.info("[租户同步] 客户端断开: sessionId={}, tenantId={}", sessionId, tenantId);
        }
    }
    
    /**
     * 消息监听器
     */
    private class TenantMessageListener implements SyncMessageListener {
        
        @Override
        public void onMessage(SyncSession session, String topic, Object data) {
            log.debug("[租户同步] 收到消息: sessionId={}, topic={}", session.getId(), topic);
            
            switch (topic) {
                case TOPIC_SYNC_REQUEST -> handleSyncRequest(session, data);
                case TOPIC_HEALTH -> handleHealthCheck(session);
                default -> log.warn("[租户同步] 未知主题: {}", topic);
            }
        }
    }
    
    /**
     * 处理同步请求
     *
     * @param session 会话
     * @param data 请求数据
     */
    @SuppressWarnings("unchecked")
    private void handleSyncRequest(SyncSession session, Object data) {
        try {
            Map<String, Object> request = (Map<String, Object>) data;
            String tenantId = (String) request.get("tenantId");
            
            if (tenantId == null || tenantId.isEmpty()) {
                protocolServer.send(session.getId(), TOPIC_SYNC_RESPONSE, Map.of(
                        "code", 400,
                        "message", "租户ID不能为空"
                ));
                return;
            }
            
            // 记录会话与租户的映射
            sessionTenantMap.put(session.getId(), tenantId);
            
            // 收集并发送元数据
            Map<String, Object> metadata = collectMetadata(tenantId);
            protocolServer.send(session.getId(), TOPIC_SYNC_RESPONSE, Map.of(
                    "code", 200,
                    "message", "success",
                    "data", metadata
            ));
            
            log.debug("[租户同步] 已发送租户 {} 元数据到 session {}", tenantId, session.getId());
        } catch (Exception e) {
            log.error("[租户同步] 处理同步请求失败", e);
            protocolServer.send(session.getId(), TOPIC_SYNC_RESPONSE, Map.of(
                    "code", 500,
                    "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 处理健康检查
     *
     * @param session 会话
     */
    private void handleHealthCheck(SyncSession session) {
        protocolServer.send(session.getId(), TOPIC_HEALTH, Map.of(
                "status", "UP",
                "service", "tenant-sync-server",
                "connections", protocolServer.getConnectionCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * 向指定租户推送元数据
     *
     * @param tenantId 租户ID
     * @param metadata 元数据
     */
    public void pushMetadata(String tenantId, Map<String, Object> metadata) {
        if (protocolServer == null) {
            log.warn("[租户同步] 服务端未启动，无法推送元数据");
            return;
        }
        
        // 查找该租户对应的所有会话
        sessionTenantMap.entrySet().stream()
                .filter(entry -> tenantId.equals(entry.getValue()))
                .forEach(entry -> {
                    String sessionId = entry.getKey();
                    protocolServer.send(sessionId, TOPIC_METADATA, Map.of(
                            "tenantId", tenantId,
                            "data", metadata,
                            "timestamp", System.currentTimeMillis()
                    ));
                    log.debug("[租户同步] 已推送元数据到租户 {} 的会话 {}", tenantId, sessionId);
                });
    }
    
    /**
     * 广播元数据到所有客户端
     *
     * @param metadata 元数据
     */
    public void broadcastMetadata(Map<String, Object> metadata) {
        if (protocolServer == null) {
            log.warn("[租户同步] 服务端未启动，无法广播元数据");
            return;
        }
        
        protocolServer.broadcast(TOPIC_METADATA, Map.of(
                "data", metadata,
                "timestamp", System.currentTimeMillis()
        ));
        log.info("[租户同步] 已广播元数据到 {} 个客户端", protocolServer.getConnectionCount());
    }

    /**
     * 启动元数据同步调度器
     */
    private void startMetadataSyncScheduler() {
        TenantProperties.SyncProtocol.MetadataSync metadataSync = tenantProperties.getSyncProtocol().getMetadataSync();

        if (!metadataSync.isEnable()) {
            log.info("[租户同步] 元数据下发未启用");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "tenant-metadata-sync");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
                this::syncAllTenants,
                metadataSync.getInitialDelay(),
                metadataSync.getInterval(),
                TimeUnit.SECONDS);

        log.info("[租户同步] 元数据下发调度器启动成功，间隔: {}秒", metadataSync.getInterval());
    }

    /**
     * 同步所有租户元数据
     * 收集并推送元数据到已连接的客户端
     */
    private void syncAllTenants() {
        try {
            log.debug("[租户同步] 开始同步所有租户元数据");
            
            // 获取所有已连接的租户
            Set<String> connectedTenants = new HashSet<>(sessionTenantMap.values());
            
            for (String tenantId : connectedTenants) {
                try {
                    Map<String, Object> metadata = collectMetadata(tenantId);
                    pushMetadata(tenantId, metadata);
                    log.debug("[租户同步] 租户 {} 元数据同步完成，共 {} 项", tenantId, metadata.size());
                } catch (Exception e) {
                    log.error("[租户同步] 租户 {} 元数据同步失败", tenantId, e);
                }
            }
            
            // 同时同步预定义的租户列表
            List<String> predefinedTenants = getTenantIds();
            for (String tenantId : predefinedTenants) {
                if (!connectedTenants.contains(tenantId)) {
                    try {
                        Map<String, Object> metadata = collectMetadata(tenantId);
                        log.debug("[租户同步] 租户 {} 元数据收集完成（未连接），共 {} 项", tenantId, metadata.size());
                    } catch (Exception e) {
                        log.error("[租户同步] 租户 {} 元数据收集失败", tenantId, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[租户同步] 同步所有租户元数据失败", e);
        }
    }


    /**
     * 收集租户元数据
     *
     * @param tenantId 租户ID
     * @return 元数据Map
     */
    private Map<String, Object> collectMetadata(String tenantId) {
        Map<String, Object> allMetadata = new HashMap<>();

        for (TenantMetadataProvider provider : providers) {
            if (!provider.supports(tenantId)) {
                continue;
            }

            try {
                Map<String, Object> metadata = provider.getMetadata(tenantId);
                if (metadata != null && !metadata.isEmpty()) {
                    allMetadata.putAll(metadata);
                    log.debug("[租户同步] 提供者 {} 为租户 {} 提供了 {} 项元数据",
                            provider.getName(), tenantId, metadata.size());
                }
            } catch (Exception e) {
                log.error("[租户同步] 提供者 {} 获取租户 {} 元数据失败",
                        provider.getName(), tenantId, e);
            }
        }

        return allMetadata;
    }

    /**
     * 获取所有租户ID
     * 子类可以重写此方法从数据库获取
     *
     * @return 租户ID列表
     */
    protected List<String> getTenantIds() {
        // 默认返回空列表，子类应该重写此方法
        return new ArrayList<>();
    }

    /**
     * 获取当前连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return protocolServer != null ? protocolServer.getConnectionCount() : 0;
    }
    
    /**
     * 获取所有已连接的租户ID
     *
     * @return 租户ID集合
     */
    public Set<String> getConnectedTenants() {
        return new HashSet<>(sessionTenantMap.values());
    }

    @Override
    public void destroy() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[租户同步] 元数据下发调度器已关闭");
        }

        if (protocolServer != null) {
            protocolServer.stop();
            log.info("[租户同步] 服务端已关闭");
        }
        
        sessionTenantMap.clear();
    }
}
