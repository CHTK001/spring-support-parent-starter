package com.chua.starter.rpc.support.loadbalance;

import java.util.List;

/**
 * RPC负载均衡策略接口
 * <p>
 * 用于在多个服务提供者中选择一个进行调用
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
public interface RpcLoadBalancer {

    /**
     * 选择一个服务提供者
     *
     * @param providers 服务提供者列表
     * @param <T>       提供者类型
     * @return 选中的提供者
     */
    <T> T select(List<T> providers);

    /**
     * 选择一个服务提供者（带上下文）
     *
     * @param providers 服务提供者列表
     * @param context   上下文信息
     * @param <T>       提供者类型
     * @return 选中的提供者
     */
    default <T> T select(List<T> providers, Object context) {
        return select(providers);
    }

    /**
     * 获取负载均衡策略名称
     *
     * @return 策略名称
     */
    String getName();
}
