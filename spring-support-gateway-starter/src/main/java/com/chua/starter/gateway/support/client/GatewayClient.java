package com.chua.starter.gateway.support.client;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.gateway.support.properties.GatewayProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 网关客户端
 * <p>
 * 将当前服务注册到发现服务，供网关服务端路由。
 *
 * @author CH
 * @since 2024/12/26
 */
@Slf4j
public class GatewayClient implements AutoCloseable {

    private final GatewayProperties properties;
    private final ServiceDiscovery serviceDiscovery;
    private Discovery registeredDiscovery;

    public GatewayClient(GatewayProperties properties, ServiceDiscovery serviceDiscovery) {
        this.properties = properties;
        this.serviceDiscovery = serviceDiscovery;
    }

    /**
     * 注册当前服务到发现服务
     */
    public void register() {
        GatewayProperties.ClientProperties clientConfig = properties.getClient();

        if (clientConfig.getServiceId() == null || clientConfig.getServiceId().isEmpty()) {
            log.warn("未配置 serviceId，无法注册到发现服务");
            return;
        }

        Project project = Project.getInstance();

        // 构建服务实例
        Discovery.DiscoveryBuilder builder = Discovery.builder()
                .serverId(project.calcApplicationUuid())
                .uriSpec(clientConfig.getServiceId())
                .host(project.getApplicationHost())
                .port(project.getApplicationPort())
                .protocol(clientConfig.getProtocol())
                .weight(clientConfig.getWeight());

        // 添加元数据
        Map<String, String> metadata = clientConfig.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            builder.metadata(metadata);
        }

        registeredDiscovery = builder.build();

        try {
            serviceDiscovery.registerService(clientConfig.getServiceId(), registeredDiscovery);
            log.info("网关客户端注册成功 - serviceId: {}, host: {}, port: {}",
                    clientConfig.getServiceId(),
                    registeredDiscovery.getHost(),
                    registeredDiscovery.getPort());
        } catch (Exception e) {
            log.error("网关客户端注册失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 注销服务
     * <p>
     * 注：当前 ServiceDiscovery 接口不支持显式注销，
     * 服务实例会通过心跳超时自动注销。
     */
    public void deregister() {
        if (registeredDiscovery == null) {
            return;
        }

        try {
            String serviceId = properties.getClient().getServiceId();
            // ServiceDiscovery 接口当前不支持显式注销，服务实例会通过心跳超时自动注销
            log.info("网关客户端关闭 - serviceId: {}", serviceId);
            registeredDiscovery = null;
        } catch (Exception e) {
            log.warn("网关客户端关闭失败: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        deregister();
    }
}
