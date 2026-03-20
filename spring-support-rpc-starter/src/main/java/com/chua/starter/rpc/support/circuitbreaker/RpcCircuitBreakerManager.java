package com.chua.starter.rpc.support.circuitbreaker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC熔断器管理器
 * <p>
 * 管理所有服务的熔断器
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class RpcCircuitBreakerManager {

    private final ConcurrentHashMap<String, RpcCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * 获取或创建熔断器
     *
     * @param serviceName 服务名称
     * @return 熔断器
     */
    public RpcCircuitBreaker getOrCreate(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, RpcCircuitBreaker::new);
    }

    /**
     * 获取熔断器
     *
     * @param serviceName 服务名称
     * @return 熔断器
     */
    public RpcCircuitBreaker get(String serviceName) {
        return circuitBreakers.get(serviceName);
    }

    /**
     * 移除熔断器
     *
     * @param serviceName 服务名称
     */
    public void remove(String serviceName) {
        circuitBreakers.remove(serviceName);
    }

    /**
     * 重置所有熔断器
     */
    public void resetAll() {
        circuitBreakers.values().forEach(RpcCircuitBreaker::reset);
    }

    /**
     * 获取所有熔断器状态
     */
    public void printStatus() {
        log.info("[RPC] 熔断器状态:");
        circuitBreakers.forEach((name, breaker) -> {
            log.info("  {} - 状态: {}, 失败次数: {}",
                name, breaker.getState(), breaker.getFailureCount().get());
        });
    }
}
