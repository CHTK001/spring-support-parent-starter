package com.chua.starter.rpc.support.loadbalance;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡策略
 *
 * @author CH
 * @since 2025-03-20
 */
@Component
public class RandomLoadBalancer implements RpcLoadBalancer {

    private final Random random = new Random();

    @Override
    public <T> T select(List<T> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        if (providers.size() == 1) {
            return providers.get(0);
        }
        return providers.get(random.nextInt(providers.size()));
    }

    @Override
    public String getName() {
        return "random";
    }
}
