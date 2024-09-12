package com.chua.starter.discovery.support.service;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.ServiceDiscovery;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 服务发现
 * @author CH
 * @since 2024/9/12
 */
@RequiredArgsConstructor
public class DiscoveryService {

    private final ServiceDiscovery serviceDiscovery;

    /**
     * 获取服务
     * @param uriSpec uri
     * @return 服务
     */
    public Discovery getDiscovery(String uriSpec) {
        return serviceDiscovery.getService(uriSpec);

    }
    /**
     * 获取服务
     * @param uriSpec uri
     * @return 服务
     */
    public Set<Discovery> getDiscoveryAll(String uriSpec) {
        return serviceDiscovery.getServiceAll(uriSpec);

    }
}
