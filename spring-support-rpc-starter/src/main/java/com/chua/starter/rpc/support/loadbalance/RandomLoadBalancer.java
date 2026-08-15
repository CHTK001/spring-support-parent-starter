package com.chua.starter.rpc.support.loadbalance;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 随机负载均衡策略（委托 {@link com.chua.common.support.lang.balance.LoadBalance} SPI 的 random 实现）
 *
 * @author CH
 * @since 2025-03-20
 */
@Component
public class RandomLoadBalancer implements RpcLoadBalancer {

    @Override
    public <T> T select(List<T> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        return com.chua.common.support.lang.balance.LoadBalance.auto("random",
                com.chua.common.support.lang.balance.BalanceConfig.builder().build())
                .select(new ArrayList<>(providers));
    }

    @Override
    public String getName() {
        return "random";
    }
}