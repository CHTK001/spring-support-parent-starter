package com.chua.starter.gateway.support.loadbalancer;

import com.chua.common.support.discovery.Discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 权重负载均衡器
 *
 * @author CH
 * @since 2024/12/26
 */
public class WeightLoadBalancer implements LoadBalancer {

    @Override
    public Discovery choose(Set<Discovery> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        List<Discovery> list = new ArrayList<>(instances);

        // 计算总权重
        double totalWeight = 0;
        for (Discovery instance : list) {
            totalWeight += Math.max(instance.getWeight(), 0.1);
        }

        // 随机选择
        double randomWeight = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double currentWeight = 0;

        for (Discovery instance : list) {
            currentWeight += Math.max(instance.getWeight(), 0.1);
            if (currentWeight >= randomWeight) {
                return instance;
            }
        }

        // 兜底返回第一个
        return list.get(0);
    }

    @Override
    public String getName() {
        return "weight";
    }
}
