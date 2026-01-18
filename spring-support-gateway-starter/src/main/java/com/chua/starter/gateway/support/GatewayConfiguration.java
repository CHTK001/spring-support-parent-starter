package com.chua.starter.gateway.support;

import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.starter.discovery.support.service.DiscoveryService;
import com.chua.starter.gateway.support.client.GatewayClient;
import com.chua.starter.gateway.support.discovery.DynamicServiceDiscoveryManager;
import com.chua.starter.gateway.support.loadbalancer.LoadBalancer;
import com.chua.starter.gateway.support.loadbalancer.RandomLoadBalancer;
import com.chua.starter.gateway.support.loadbalancer.RoundRobinLoadBalancer;
import com.chua.starter.gateway.support.loadbalancer.WeightLoadBalancer;
import com.chua.starter.gateway.support.properties.GatewayProperties;
import com.chua.starter.gateway.support.route.DiscoveryRouteLocator;
import com.chua.starter.gateway.support.server.GatewayServerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关自动配置
 *
 * @author CH
 * @since 2024/12/26
 */
@Slf4j
@EnableConfigurationProperties(GatewayProperties.class)
@ConditionalOnProperty(prefix = GatewayProperties.PREFIX, name = "enable", havingValue = "true")
@EnableScheduling
public class GatewayConfiguration {

    /**
     * 创建负载均衡器
     */
    @Bean
    @ConditionalOnMissingBean
    public LoadBalancer loadBalancer(GatewayProperties properties) {
        String strategy = properties.getServer().getLoadBalancer();
        log.info(">>>>>>> 初始化负载均衡器: {}", strategy);

        return switch (strategy.toLowerCase()) {
            case "random" -> new RandomLoadBalancer();
            case "weight" -> new WeightLoadBalancer();
            default -> new RoundRobinLoadBalancer();
        };
    }

    /**
     * 创建路由定位器
     */
    @Bean
    @ConditionalOnMissingBean
    public DiscoveryRouteLocator discoveryRouteLocator(DiscoveryService discoveryService, LoadBalancer loadBalancer) {
        log.info(">>>>>>> 初始化路由定位器");
        return new DiscoveryRouteLocator(discoveryService, loadBalancer);
    }

    /**
     * 服务端模式 - 创建网关服务端管理器
     */
    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = GatewayProperties.PREFIX, name = "mode", havingValue = "server", matchIfMissing = true)
    public GatewayServerManager gatewayServerManager(GatewayProperties properties, DiscoveryRouteLocator routeLocator) {
        log.info(">>>>>>> 初始化网关服务端管理器");
        return new GatewayServerManager(properties, routeLocator);
    }

    /**
     * 服务端模式 - 创建动态服务发现管理器
     */
    @Bean(initMethod = "initialize", destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = GatewayProperties.PREFIX, name = "mode", havingValue = "server", matchIfMissing = true)
    @ConditionalOnProperty(prefix = GatewayProperties.PREFIX + ".server.discovery", name = "enabled", havingValue = "true")
    public DynamicServiceDiscoveryManager dynamicServiceDiscoveryManager(
            GatewayProperties properties, DiscoveryRouteLocator routeLocator) {
        log.info(">>>>>>> 初始化动态服务发现管理器");
        return new DynamicServiceDiscoveryManager(properties, routeLocator);
    }

    /**
     * 客户端模式 - 创建网关客户端
     */
    @Bean(initMethod = "register", destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = GatewayProperties.PREFIX, name = "mode", havingValue = "client")
    public GatewayClient gatewayClient(GatewayProperties properties, ServiceDiscovery serviceDiscovery) {
        log.info(">>>>>>> 初始化网关客户端");
        return new GatewayClient(properties, serviceDiscovery);
    }
}
