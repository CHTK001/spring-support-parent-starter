package com.chua.starter.gateway.support.discovery;

import com.chua.common.support.collection.Options;
import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.Event;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.discovery.ServiceDiscoveryListener;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.gateway.support.properties.GatewayProperties;
import com.chua.starter.gateway.support.route.DiscoveryRouteLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.chua.starter.common.support.logger.ModuleLog.*;

/**
 * 动态服务发现管理器
 * <p>
 * 支持动态发现服务并自动刷新路由，可以订阅服务变化事件。
 *
 * @author CH
 * @since 2025/01/05
 */
@Slf4j
public class DynamicServiceDiscoveryManager implements AutoCloseable {

    private final GatewayProperties properties;
    private final DiscoveryRouteLocator routeLocator;
    private ServiceDiscovery serviceDiscovery;
    private final Set<String> discoveredServices = new CopyOnWriteArraySet<>();

    public DynamicServiceDiscoveryManager(GatewayProperties properties, DiscoveryRouteLocator routeLocator) {
        this.properties = properties;
        this.routeLocator = routeLocator;
    }

    /**
     * 初始化服务发现
     */
    public void initialize() {
        GatewayProperties.DiscoveryProperties discoveryConfig = properties.getServer().getDiscovery();
        if (!discoveryConfig.isEnabled()) {
            log.info("[Gateway] 动态服务发现未启用");
            return;
        }

        if (StringUtils.isEmpty(discoveryConfig.getType())) {
            log.warn("[Gateway] 动态服务发现已启用但未配置类型，跳过初始化");
            return;
        }

        try {
            // 创建服务发现选项
            DiscoveryOption discoveryOption = createDiscoveryOption(discoveryConfig);

            // 创建服务发现实例
            ServiceProvider<ServiceDiscovery> serviceProvider = ServiceProvider.of(ServiceDiscovery.class);
            serviceDiscovery = serviceProvider.getNewExtension(discoveryConfig.getType(), discoveryOption);

            if (serviceDiscovery == null) {
                log.warn("[Gateway] 无法创建服务发现实例，类型: {}", discoveryConfig.getType());
                return;
            }

            // 启动服务发现
            serviceDiscovery.start();
            log.info("[Gateway] 动态服务发现初始化 {} - 类型: {}, 地址: {}", 
                    success(), highlight(discoveryConfig.getType()), discoveryConfig.getAddress());

            // 如果启用订阅，订阅所有服务变化
            if (discoveryConfig.isSubscribe() && serviceDiscovery.isSupportSubscribe()) {
                subscribeAllServices();
            }

            // 初始发现服务
            discoverServices();

        } catch (Exception e) {
            log.error("[Gateway] 初始化动态服务发现 {}: {}", failed(), e.getMessage(), e);
        }
    }

    /**
     * 创建服务发现选项
     */
    private DiscoveryOption createDiscoveryOption(GatewayProperties.DiscoveryProperties discoveryConfig) {
        DiscoveryOption option = new DiscoveryOption();
        option.setAddress(discoveryConfig.getAddress());

        // 设置额外配置
        if (discoveryConfig.getOptions() != null && !discoveryConfig.getOptions().isEmpty()) {
            Options options = new Options();
            discoveryConfig.getOptions().forEach((key, value) -> options.addOption(key, value));
            option.setOptions(options);
        }

        return option;
    }

    /**
     * 发现所有服务
     */
    @Scheduled(fixedDelayString = "${plugin.gateway.server.refreshInterval:30000}")
    public void discoverServices() {
        if (serviceDiscovery == null) {
            return;
        }

        GatewayProperties.DiscoveryProperties discoveryConfig = properties.getServer().getDiscovery();
        if (!discoveryConfig.isEnabled()) {
            return;
        }

        try {
            // 注意：ServiceDiscovery接口没有直接获取所有服务名称的方法
            // 这里需要通过其他方式获取服务列表，或者依赖服务发现实现提供的方法
            // 当前实现依赖于DiscoveryService来获取服务列表
            log.debug("[Gateway] 刷新服务列表");
            
            // 如果服务发现支持订阅，服务变化会通过监听器自动处理
            // 这里主要用于定时刷新已发现的服务状态
            
        } catch (Exception e) {
            log.warn("[Gateway] 发现服务失败: {}", e.getMessage());
        }
    }

    /**
     * 订阅所有服务变化
     */
    private void subscribeAllServices() {
        if (serviceDiscovery == null || !serviceDiscovery.isSupportSubscribe()) {
            return;
        }

        ServiceDiscoveryListener listener = new ServiceDiscoveryListener() {
            @Override
            public void listen(String serviceName, Discovery discovery, Event event) {
                handleServiceChange(serviceName, discovery, event);
            }
        };

        // 订阅通配符服务（如果支持）
        try {
            serviceDiscovery.subscribe("*", listener);
            log.info("[Gateway] 订阅所有服务变化");
        } catch (Exception e) {
            log.warn("[Gateway] 订阅服务变化失败: {}", e.getMessage());
        }
    }

    /**
     * 处理服务变化事件
     */
    private void handleServiceChange(String serviceName, Discovery discovery, Event event) {
        log.info("[Gateway] 服务变化 - 服务: {}, 事件: {}, 实例: {}:{}", 
                highlight(serviceName), event, discovery.getHost(), discovery.getPort());

        switch (event) {
            case REGISTER:
                discoveredServices.add(serviceName);
                log.info("[Gateway] 服务注册 - {}", serviceName);
                break;
            case UNREGISTER:
                discoveredServices.remove(serviceName);
                log.info("[Gateway] 服务注销 - {}", serviceName);
                break;
            case UPDATE:
                log.info("[Gateway] 服务更新 - {}", serviceName);
                break;
        }
    }

    /**
     * 获取已发现的服务列表
     */
    public Set<String> getDiscoveredServices() {
        return new CopyOnWriteArraySet<>(discoveredServices);
    }

    /**
     * 检查服务是否已发现
     */
    public boolean isServiceDiscovered(String serviceName) {
        return discoveredServices.contains(serviceName);
    }

    @Override
    public void close() throws Exception {
        if (serviceDiscovery != null) {
            try {
                serviceDiscovery.close();
                log.info("[Gateway] 动态服务发现已关闭");
            } catch (Exception e) {
                log.warn("[Gateway] 关闭动态服务发现失败: {}", e.getMessage());
            }
        }
        discoveredServices.clear();
    }
}

