package com.chua.starter.gateway.support.loadbalancer;

import com.chua.common.support.discovery.Discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器
 *
 * @author CH
 * @since 2024/12/26
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Discovery choose(Set<Discovery> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        List<Discovery> list = new ArrayList<>(instances);
        int index = Math.abs(counter.getAndIncrement() % list.size());
        return list.get(index);
    }

    @Override
    public String getName() {
        return "round-robin";
    }
}
