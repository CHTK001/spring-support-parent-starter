package com.chua.starter.gateway.support.route;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.starter.discovery.support.service.DiscoveryService;
import com.chua.starter.gateway.support.loadbalancer.LoadBalancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 基于发现服务的路由定位器
 *
 * @author CH
 * @since 2024/12/26
 */
@Slf4j
@RequiredArgsConstructor
public class DiscoveryRouteLocator {

    private final DiscoveryService discoveryService;
    private final LoadBalancer loadBalancer;

    /**
     * 根据服务ID获取目标地址
     *
     * @param serviceId 服务ID
     * @return 目标Discovery实例
     */
    public Discovery locate(String serviceId) {
        Set<Discovery> instances = discoveryService.getDiscoveryAll(serviceId);
        if (instances == null || instances.isEmpty()) {
            log.warn("未找到服务实例: {}", serviceId);
            return null;
        }

        Discovery chosen = loadBalancer.choose(instances);
        if (chosen != null) {
            log.debug("路由到服务实例: {} -> {}:{}", serviceId, chosen.getHost(), chosen.getPort());
        }
        return chosen;
    }

    /**
     * 获取所有服务实例
     *
     * @param serviceId 服务ID
     * @return 服务实例集合
     */
    public Set<Discovery> getAllInstances(String serviceId) {
        return discoveryService.getDiscoveryAll(serviceId);
    }

    /**
     * 创建目标URL
     *
     * @param serviceId   服务ID
     * @param requestPath 请求路径
     * @return 完整的目标URL
     */
    public String createTargetUrl(String serviceId, String requestPath) {
        Discovery discovery = locate(serviceId);
        if (discovery == null) {
            return null;
        }
        return discovery.createUrl(requestPath);
    }
}
