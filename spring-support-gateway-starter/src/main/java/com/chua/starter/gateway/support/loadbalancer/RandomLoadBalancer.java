package com.chua.starter.gateway.support.loadbalancer;

import com.chua.common.support.discovery.Discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡器
 *
 * @author CH
 * @since 2024/12/26
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public Discovery choose(Set<Discovery> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        List<Discovery> list = new ArrayList<>(instances);
        int index = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(index);
    }

    @Override
    public String getName() {
        return "random";
    }
}
