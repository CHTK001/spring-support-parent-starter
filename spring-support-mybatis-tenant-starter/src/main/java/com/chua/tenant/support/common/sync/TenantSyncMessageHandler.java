package com.chua.tenant.support.common.sync;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.sync.support.spi.SyncMessageHandler;
import com.chua.tenant.support.client.consumer.TenantMetadataConsumer;
import com.chua.tenant.support.client.handler.TenantHandler;
import com.chua.tenant.support.client.handler.TenantServiceHandler;
import com.chua.tenant.support.common.entity.SysTenant;
import com.chua.tenant.support.common.properties.TenantProperties;
import com.chua.tenant.support.server.provider.TenantMetadataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 租户同步消息处理器
 * <p>
 * 实现 SyncMessageHandler 接口，处理租户相关的同步消息。
 * 服务端收集元数据，客户端消费元数据。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Component
@Spi("tenant")
@ConditionalOnClass(SyncMessageHandler.class)
@ConditionalOnProperty(prefix = TenantProperties.PRE, name = {"enable", "sync.enable"}, havingValue = "true")
public class TenantSyncMessageHandler implements SyncMessageHandler {

    /**
     * 租户同步主题
     */
    public static final String TOPIC_TENANT_METADATA = "tenant/metadata";
    public static final String TOPIC_TENANT_SYNC_REQUEST = "tenant/sync/request";
    public static final String TOPIC_TENANT_SYNC_RESPONSE = "tenant/sync/response";
    public static final String TOPIC_TENANT_UPDATE = "tenant/update";
    public static final String TOPIC_TENANT_DELETE = "tenant/delete";
    public static final String TOPIC_TENANT_SERVICE_UPDATE = "tenant/service/update";
    public static final String TOPIC_TENANT_SERVICE_DELETE = "tenant/service/delete";

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired(required = false)
    private List<TenantHandler> tenantHandlers;

    @Autowired(required = false)
    private List<TenantServiceHandler> tenantServiceHandlers;

    /**
     * 元数据提供者列表（服务端使用）
     */
    private List<TenantMetadataProvider> providers = new ArrayList<>();

    /**
     * 元数据消费者列表（客户端使用）
     */
    private List<TenantMetadataConsumer> consumers = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadProviders();
        loadConsumers();
        log.info("[租户同步] TenantSyncMessageHandler 已启动，模式: {}", tenantProperties.getMode());
    }

    /**
     * 加载所有元数据提供者（服务端）
     */
    private void loadProviders() {
        try {
            ServiceProvider<TenantMetadataProvider> serviceProvider = ServiceProvider.of(TenantMetadataProvider.class);
            providers = new ArrayList<>(serviceProvider.collect());
            providers.sort(Comparator.comparingInt(TenantMetadataProvider::getOrder));
            log.info("[租户同步] 加载了 {} 个元数据提供者", providers.size());
        } catch (Exception e) {
            log.debug("[租户同步] 加载元数据提供者失败: {}", e.getMessage());
        }
    }

    /**
     * 加载所有元数据消费者（客户端）
     */
    private void loadConsumers() {
        try {
            ServiceProvider<TenantMetadataConsumer> serviceProvider = ServiceProvider.of(TenantMetadataConsumer.class);
            consumers = new ArrayList<>(serviceProvider.collect());
            consumers.sort(Comparator.comparingInt(TenantMetadataConsumer::getOrder));
            log.info("[租户同步] 加载了 {} 个元数据消费者", consumers.size());
        } catch (Exception e) {
            log.debug("[租户同步] 加载元数据消费者失败: {}", e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "tenant";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean supports(String topic) {
        return topic != null && topic.startsWith("tenant/");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        log.debug("[租户同步] 收到消息: topic={}, sessionId={}", topic, sessionId);

        return switch (topic) {
            case TOPIC_TENANT_SYNC_REQUEST -> handleSyncRequest(sessionId, data);
            case TOPIC_TENANT_SYNC_RESPONSE -> handleSyncResponse(data);
            case TOPIC_TENANT_METADATA -> handleMetadataPush(data);
            case TOPIC_TENANT_UPDATE -> handleTenantUpdate(data);
            case TOPIC_TENANT_DELETE -> handleTenantDelete(data);
            case TOPIC_TENANT_SERVICE_UPDATE -> handleServiceUpdate(data);
            case TOPIC_TENANT_SERVICE_DELETE -> handleServiceDelete(data);
            default -> {
                log.warn("[租户同步] 未知主题: {}", topic);
                yield null;
            }
        };
    }

    /**
     * 处理同步请求（服务端）
     */
    private Object handleSyncRequest(String sessionId, Map<String, Object> data) {
        if (!tenantProperties.isServerMode()) {
            log.warn("[租户同步] 客户端模式不处理同步请求");
            return Map.of("code", 400, "message", "非服务端模式");
        }

        String tenantId = (String) data.get("tenantId");
        if (tenantId == null || tenantId.isEmpty()) {
            return Map.of("code", 400, "message", "租户ID不能为空");
        }

        try {
            Map<String, Object> metadata = collectMetadata(tenantId);
            return Map.of(
                    "code", 200,
                    "message", "success",
                    "tenantId", tenantId,
                    "data", metadata
            );
        } catch (Exception e) {
            log.error("[租户同步] 处理同步请求失败", e);
            return Map.of("code", 500, "message", e.getMessage());
        }
    }

    /**
     * 处理同步响应（客户端）
     */
    @SuppressWarnings("unchecked")
    private Object handleSyncResponse(Map<String, Object> data) {
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        Integer code = (Integer) data.get("code");
        if (code != null && code == 200) {
            String tenantId = (String) data.get("tenantId");
            Map<String, Object> metadata = (Map<String, Object>) data.get("data");

            if (tenantId != null && metadata != null && !metadata.isEmpty()) {
                processMetadata(tenantId, metadata);
                log.info("[租户同步] 租户 {} 元数据同步成功，共 {} 项", tenantId, metadata.size());
            }
        } else {
            log.warn("[租户同步] 同步失败: {}", data.get("message"));
        }
        return null;
    }

    /**
     * 处理元数据推送（客户端）
     */
    @SuppressWarnings("unchecked")
    private Object handleMetadataPush(Map<String, Object> data) {
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        String tenantId = (String) data.get("tenantId");
        Map<String, Object> metadata = (Map<String, Object>) data.get("data");

        if (tenantId != null && metadata != null && !metadata.isEmpty()) {
            processMetadata(tenantId, metadata);
            log.info("[租户同步] 收到租户 {} 元数据推送，共 {} 项", tenantId, metadata.size());
        }
        return null;
    }

    /**
     * 处理租户更新（客户端）
     */
    @SuppressWarnings("unchecked")
    private Object handleTenantUpdate(Map<String, Object> data) {
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        if (tenantHandlers == null || tenantHandlers.isEmpty()) {
            log.debug("[租户同步] 无可用的 TenantHandler");
            return null;
        }

        try {
            SysTenant tenant = convertToTenant(data);
            for (TenantHandler handler : tenantHandlers) {
                try {
                    handler.saveOrUpdate(tenant);
                    log.debug("[租户同步] TenantHandler {} 处理完成", handler.getClass().getSimpleName());
                } catch (Exception e) {
                    log.error("[租户同步] TenantHandler {} 处理失败", handler.getClass().getSimpleName(), e);
                }
            }
            log.info("[租户同步] 租户 {} 更新成功", tenant.getSysTenantCode());
        } catch (Exception e) {
            log.error("[租户同步] 租户更新失败", e);
        }
        return null;
    }

    /**
     * 处理租户删除（客户端）
     */
    private Object handleTenantDelete(Map<String, Object> data) {
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        if (tenantHandlers == null || tenantHandlers.isEmpty()) {
            return null;
        }

        try {
            SysTenant tenant = convertToTenant(data);
            for (TenantHandler handler : tenantHandlers) {
                try {
                    handler.delete(tenant);
                } catch (Exception e) {
                    log.error("[租户同步] TenantHandler {} 删除失败", handler.getClass().getSimpleName(), e);
                }
            }
            log.info("[租户同步] 租户 {} 删除成功", tenant.getSysTenantCode());
        } catch (Exception e) {
            log.error("[租户同步] 租户删除失败", e);
        }
        return null;
    }

    /**
     * 处理服务更新（客户端）
     */
    @SuppressWarnings("unchecked")
    private Object handleServiceUpdate(Map<String, Object> data) {
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        if (tenantServiceHandlers == null || tenantServiceHandlers.isEmpty()) {
            log.debug("[租户同步] 无可用的 TenantServiceHandler");
            return null;
        }

        try {
            Integer tenantId = (Integer) data.get("tenantId");
            List<Integer> menuIds = (List<Integer>) data.get("menuIds");

            if (tenantId == null || menuIds == null) {
                log.warn("[租户同步] 服务更新数据不完整");
                return null;
            }

            for (TenantServiceHandler handler : tenantServiceHandlers) {
                try {
                    handler.saveOrUpdate(tenantId, menuIds);
                    log.debug("[租户同步] TenantServiceHandler {} 处理完成", handler.getClass().getSimpleName());
                } catch (Exception e) {
                    log.error("[租户同步] TenantServiceHandler {} 处理失败", handler.getClass().getSimpleName(), e);
                }
            }
            log.info("[租户同步] 租户 {} 服务更新成功，菜单数: {}", tenantId, menuIds.size());
        } catch (Exception e) {
            log.error("[租户同步] 服务更新失败", e);
        }
        return null;
    }

    /**
     * 处理服务删除（客户端）
     */
    @SuppressWarnings("unchecked")
    private Object handleServiceDelete(Map<String, Object> data) {
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        if (tenantServiceHandlers == null || tenantServiceHandlers.isEmpty()) {
            return null;
        }

        try {
            Integer tenantId = (Integer) data.get("tenantId");
            List<Integer> menuIds = (List<Integer>) data.get("menuIds");

            if (tenantId != null && menuIds != null) {
                for (TenantServiceHandler handler : tenantServiceHandlers) {
                    try {
                        handler.delete(tenantId, menuIds);
                    } catch (Exception e) {
                        log.error("[租户同步] TenantServiceHandler {} 删除失败", handler.getClass().getSimpleName(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[租户同步] 服务删除失败", e);
        }
        return null;
    }

    /**
     * 收集租户元数据（服务端使用）
     */
    public Map<String, Object> collectMetadata(String tenantId) {
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
                log.error("[租户同步] 提供者 {} 获取租户 {} 元数据失败", provider.getName(), tenantId, e);
            }
        }

        return allMetadata;
    }

    /**
     * 处理元数据（客户端使用）
     */
    private void processMetadata(String tenantId, Map<String, Object> metadata) {
        for (TenantMetadataConsumer consumer : consumers) {
            try {
                consumer.consumeMetadata(tenantId, metadata);
                log.debug("[租户同步] 消费者 {} 处理租户 {} 元数据完成", consumer.getName(), tenantId);
            } catch (Exception e) {
                log.error("[租户同步] 消费者 {} 处理租户 {} 元数据失败", consumer.getName(), tenantId, e);
            }
        }
    }

    /**
     * 转换为租户对象
     */
    private SysTenant convertToTenant(Map<String, Object> data) {
        SysTenant tenant = new SysTenant();
        tenant.setSysTenantId((Integer) data.get("sysTenantId"));
        tenant.setSysTenantCode((String) data.get("sysTenantCode"));
        tenant.setSysTenantName((String) data.get("sysTenantName"));
        tenant.setSysTenantUsername((String) data.get("sysTenantUsername"));
        tenant.setSysTenantPassword((String) data.get("sysTenantPassword"));
        tenant.setSysTenantPhone((String) data.get("sysTenantPhone"));
        tenant.setSysTenantEmail((String) data.get("sysTenantEmail"));
        tenant.setSysTenantStatus((Integer) data.get("sysTenantStatus"));
        return tenant;
    }
}
