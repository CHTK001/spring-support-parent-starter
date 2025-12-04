package com.chua.tenant.support.sync.handler;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.sync.support.spi.SyncMessageHandler;
import com.chua.tenant.support.sync.TenantMetadataConsumer;
import com.chua.tenant.support.sync.TenantMetadataProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 租户同步消息处理器
 * <p>
 * 实现 SyncMessageHandler 接口，处理租户相关的同步消息
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Slf4j
@Spi("tenant")
public class TenantSyncMessageHandler implements SyncMessageHandler {

    /**
     * 租户主题
     */
    public static final String TOPIC_METADATA = "tenant/metadata";
    public static final String TOPIC_SYNC_REQUEST = "tenant/sync/request";
    public static final String TOPIC_SYNC_RESPONSE = "tenant/sync/response";

    /**
     * 元数据提供者列表（服务端使用）
     */
    private List<TenantMetadataProvider> providers;

    /**
     * 元数据消费者列表（客户端使用）
     */
    private List<TenantMetadataConsumer> consumers;

    /**
     * 响应回调（由外部设置）
     */
    private ResponseCallback responseCallback;

    public TenantSyncMessageHandler() {
        loadProviders();
        loadConsumers();
    }

    /**
     * 加载所有元数据提供者
     */
    private void loadProviders() {
        ServiceProvider<TenantMetadataProvider> serviceProvider = ServiceProvider.of(TenantMetadataProvider.class);
        providers = new ArrayList<>(serviceProvider.collect());
        providers.sort(Comparator.comparingInt(TenantMetadataProvider::getOrder));
        log.info("[租户同步] 加载了 {} 个元数据提供者", providers.size());
    }

    /**
     * 加载所有元数据消费者
     */
    private void loadConsumers() {
        ServiceProvider<TenantMetadataConsumer> serviceProvider = ServiceProvider.of(TenantMetadataConsumer.class);
        consumers = new ArrayList<>(serviceProvider.collect());
        consumers.sort(Comparator.comparingInt(TenantMetadataConsumer::getOrder));
        log.info("[租户同步] 加载了 {} 个元数据消费者", consumers.size());
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
        log.debug("[租户同步] 处理消息: topic={}, sessionId={}", topic, sessionId);

        return switch (topic) {
            case TOPIC_SYNC_REQUEST -> handleSyncRequest(sessionId, data);
            case TOPIC_SYNC_RESPONSE -> handleSyncResponse(data);
            case TOPIC_METADATA -> handleMetadataPush(data);
            default -> {
                log.warn("[租户同步] 未知主题: {}", topic);
                yield null;
            }
        };
    }

    /**
     * 处理同步请求（服务端）
     */
    @SuppressWarnings("unchecked")
    private Object handleSyncRequest(String sessionId, Map<String, Object> data) {
        String tenantId = (String) data.get("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            return Map.of(
                    "code", 400,
                    "message", "租户ID不能为空"
            );
        }

        try {
            // 收集元数据
            Map<String, Object> metadata = collectMetadata(tenantId);
            return Map.of(
                    "code", 200,
                    "message", "success",
                    "tenantId", tenantId,
                    "data", metadata
            );
        } catch (Exception e) {
            log.error("[租户同步] 处理同步请求失败", e);
            return Map.of(
                    "code", 500,
                    "message", e.getMessage()
            );
        }
    }

    /**
     * 处理同步响应（客户端）
     */
    @SuppressWarnings("unchecked")
    private Object handleSyncResponse(Map<String, Object> data) {
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
        String tenantId = (String) data.get("tenantId");
        Map<String, Object> metadata = (Map<String, Object>) data.get("data");

        if (tenantId != null && metadata != null && !metadata.isEmpty()) {
            processMetadata(tenantId, metadata);
            log.info("[租户同步] 收到租户 {} 元数据推送，共 {} 项", tenantId, metadata.size());
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
                log.error("[租户同步] 提供者 {} 获取租户 {} 元数据失败",
                        provider.getName(), tenantId, e);
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
                log.debug("[租户同步] 消费者 {} 处理租户 {} 元数据完成",
                        consumer.getName(), tenantId);
            } catch (Exception e) {
                log.error("[租户同步] 消费者 {} 处理租户 {} 元数据失败",
                        consumer.getName(), tenantId, e);
            }
        }
    }

    /**
     * 设置响应回调
     */
    public void setResponseCallback(ResponseCallback callback) {
        this.responseCallback = callback;
    }

    /**
     * 响应回调接口
     */
    @FunctionalInterface
    public interface ResponseCallback {
        void send(String sessionId, String topic, Object data);
    }
}
