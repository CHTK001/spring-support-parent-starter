package com.chua.tenant.support.sync.server;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.tenant.support.properties.TenantProperties;
import com.chua.tenant.support.sync.TenantMetadataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * 租户同步服务端
 * 负责启动HTTP服务器，提供租户元数据下发接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/02
 */
@Slf4j
public class TenantSyncServer implements InitializingBean, DisposableBean {

    private final TenantProperties tenantProperties;
    private WebServer webServer;
    private ScheduledExecutorService scheduler;
    private final List<TenantMetadataProvider> providers = new ArrayList<>();

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
     * 启动HTTP服务器
     */
    private void startServer() {
        try {
            int port = tenantProperties.getSyncProtocol().getServerPort();

            RouterFunction<ServerResponse> route = RouterFunctions
                    .route(GET("/tenant/metadata/{tenantId}"), request -> {
                        String tenantId = request.pathVariable("tenantId");
                        return getMetadata(tenantId);
                    })
                    .andRoute(POST("/tenant/sync"), request -> {
                        return request.bodyToMono(Map.class)
                                .flatMap(this::syncMetadata);
                    })
                    .andRoute(GET("/tenant/health"),
                            request -> ok().bodyValue(Map.of("status", "UP", "service", "tenant-sync-server")));

            HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);
            ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);

            ReactiveWebServerFactory factory = new NettyReactiveWebServerFactory(port);
            webServer = factory.getWebServer(adapter);
            webServer.start();

            log.info("[租户同步] 服务端启动成功，端口: {}", port);
        } catch (Exception e) {
            log.error("[租户同步] 服务端启动失败", e);
        }
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
     */
    private void syncAllTenants() {
        try {
            log.debug("[租户同步] 开始同步所有租户元数据");
            // 这里可以从数据库获取所有租户ID
            // 暂时使用示例数据
            List<String> tenantIds = getTenantIds();

            for (String tenantId : tenantIds) {
                try {
                    Map<String, Object> metadata = collectMetadata(tenantId);
                    log.debug("[租户同步] 租户 {} 元数据收集完成，共 {} 项", tenantId, metadata.size());
                } catch (Exception e) {
                    log.error("[租户同步] 租户 {} 元数据收集失败", tenantId, e);
                }
            }
        } catch (Exception e) {
            log.error("[租户同步] 同步所有租户元数据失败", e);
        }
    }

    /**
     * 获取租户元数据
     *
     * @param tenantId 租户ID
     * @return 元数据响应
     */
    private Mono<ServerResponse> getMetadata(String tenantId) {
        try {
            Map<String, Object> metadata = collectMetadata(tenantId);
            return ok().bodyValue(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", metadata));
        } catch (Exception e) {
            log.error("[租户同步] 获取租户 {} 元数据失败", tenantId, e);
            return ServerResponse.status(500).bodyValue(Map.of(
                    "code", 500,
                    "message", e.getMessage()));
        }
    }

    /**
     * 同步元数据
     *
     * @param request 同步请求
     * @return 同步响应
     */
    private Mono<ServerResponse> syncMetadata(Map<String, Object> request) {
        try {
            String tenantId = (String) request.get("tenantId");
            if (tenantId == null) {
                return ServerResponse.badRequest().bodyValue(Map.of(
                        "code", 400,
                        "message", "tenantId is required"));
            }

            Map<String, Object> metadata = collectMetadata(tenantId);
            return ok().bodyValue(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", metadata));
        } catch (Exception e) {
            log.error("[租户同步] 同步元数据失败", e);
            return ServerResponse.status(500).bodyValue(Map.of(
                    "code", 500,
                    "message", e.getMessage()));
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

    @Override
    public void destroy() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[租户同步] 元数据下发调度器已关闭");
        }

        if (webServer != null) {
            webServer.stop();
            log.info("[租户同步] 服务端已关闭");
        }
    }
}
