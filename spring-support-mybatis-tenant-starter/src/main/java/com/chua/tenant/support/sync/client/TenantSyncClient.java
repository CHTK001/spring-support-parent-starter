package com.chua.tenant.support.sync.client;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.tenant.support.properties.TenantProperties;
import com.chua.tenant.support.sync.TenantMetadataConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 租户同步客户端
 * 负责从服务端拉取租户元数据并分发给消费者处理
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/02
 */
@Slf4j
public class TenantSyncClient implements InitializingBean, DisposableBean {

    private final TenantProperties tenantProperties;
    private final RestTemplate restTemplate;
    private ScheduledExecutorService scheduler;
    private final List<TenantMetadataConsumer> consumers = new ArrayList<>();

    public TenantSyncClient(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
        this.restTemplate = new RestTemplate();
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

        startMetadataSyncScheduler();
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
                this::syncMetadata,
                metadataSync.getInitialDelay(),
                metadataSync.getInterval(),
                TimeUnit.SECONDS);

        log.info("[租户同步] 元数据同步调度器启动成功，间隔: {}秒", metadataSync.getInterval());
    }

    /**
     * 同步元数据
     */
    private void syncMetadata() {
        try {
            log.debug("[租户同步] 开始同步元数据");

            String serverAddress = tenantProperties.getSyncProtocol().getServerAddress();
            String url = serverAddress + "/tenant/sync";

            // 获取当前租户ID（从上下文或配置中获取）
            String tenantId = getCurrentTenantId();
            if (tenantId == null) {
                log.warn("[租户同步] 无法获取当前租户ID，跳过同步");
                return;
            }

            Map<String, Object> request = new HashMap<>();
            request.put("tenantId", tenantId);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");

                if (code != null && code == 200) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) body.get("data");

                    if (metadata != null && !metadata.isEmpty()) {
                        processMetadata(tenantId, metadata);
                        log.info("[租户同步] 租户 {} 元数据同步成功，共 {} 项", tenantId, metadata.size());
                    } else {
                        log.debug("[租户同步] 租户 {} 没有可同步的元数据", tenantId);
                    }
                } else {
                    log.warn("[租户同步] 服务端返回错误: {}", body.get("message"));
                }
            } else {
                log.warn("[租户同步] 服务端响应异常: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("[租户同步] 同步元数据失败", e);
        }
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
        try {
            String serverAddress = tenantProperties.getSyncProtocol().getServerAddress();
            String url = serverAddress + "/tenant/metadata/" + tenantId;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");

                if (code != null && code == 200) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) body.get("data");

                    if (metadata != null && !metadata.isEmpty()) {
                        processMetadata(tenantId, metadata);
                        log.info("[租户同步] 手动同步租户 {} 元数据成功", tenantId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[租户同步] 手动同步租户 {} 元数据失败", tenantId, e);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[租户同步] 元数据同步调度器已关闭");
        }
    }
}
