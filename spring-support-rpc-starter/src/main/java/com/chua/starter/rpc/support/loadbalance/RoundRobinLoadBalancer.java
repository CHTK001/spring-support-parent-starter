package com.chua.starter.rpc.support.loadbalance;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡策略
 *
 * @author CH
 * @since 2025-03-20
 */
@Component
public class RoundRobinLoadBalancer implements RpcLoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public <T> T select(List<T> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        if (providers.size() == 1) {
            return providers.get(0);
        }

        int index = Math.abs(counter.getAndIncrement() % providers.size());
        return providers.get(index);
    }

    @Override
    public String getName() {
        return "round-robin";
    }
}
