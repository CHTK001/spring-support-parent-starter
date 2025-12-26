package com.chua.starter.gateway.support.loadbalancer;

import com.chua.common.support.discovery.Discovery;

import java.util.Set;

/**
 * 负载均衡器接口
 *
 * @author CH
 * @since 2024/12/26
 */
public interface LoadBalancer {

    /**
     * 从服务实例列表中选择一个
     *
     * @param instances 服务实例列表
     * @return 选中的服务实例，如果列表为空返回null
     */
    Discovery choose(Set<Discovery> instances);

    /**
     * 获取负载均衡器名称
     *
     * @return 名称
     */
    String getName();
}
