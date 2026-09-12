package com.chua.starter.rpc.support.loadbalance;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
* 轮询负载均衡策略（委托 {@link com.chua.common.support.lang.balance.LoadBalance} SPI 的 round 实现）
*
* @since 2025-03-20
* @author CH
 */
@Component
public class RoundRobinLoadBalancer implements RpcLoadBalancer {

    @Override
    /** 选择 */
    public <T> T select(List<T> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        return com.chua.common.support.lang.balance.LoadBalance.auto("round",
                com.chua.common.support.lang.balance.BalanceConfig.builder().build())
                .select(new ArrayList<>(providers));
    }

    @Override
    /** 获取名称 */
    public String getName() {
        return "round-robin";
    }
}