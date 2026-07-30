package com.chua.starter.rpc.support.circuitbreaker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC熔断器
 * <p>
 * 实现熔断保护机制，防止故障扩散
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Data
public class RpcCircuitBreaker {

    /**
     * 熔断器状态
     */
    public enum State {
        CLOSED,      // 关闭状态（正常）
        OPEN,        // 打开状态（熔断）
        HALF_OPEN    // 半开状态（尝试恢复）
    }

    /**
     * 当前状态
     */
    private volatile State state = State.CLOSED;

    /**
     * 失败次数阈值
     */
    private int failureThreshold = 5;

    /**
     * 成功次数阈值（半开状态）
     */
    private int successThreshold = 2;

    /**
     * 超时时间（打开状态持续时间）
     */
    private Duration timeout = Duration.ofSeconds(60);

    /**
     * 失败计数器
     */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /**
     * 成功计数器
     */
    private final AtomicInteger successCount = new AtomicInteger(0);

    /**
     * 打开时间
     */
    private final AtomicLong openTime = new AtomicLong(0);

    /**
     * 服务名称
     */
    private String serviceName;

    public RpcCircuitBreaker(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * 尝试调用
     *
     * @return 是否允许调用
     */
    public boolean tryAcquire() {
        if (state == State.CLOSED) {
            return true;
        }

        if (state == State.OPEN) {
            // 检查是否超时，可以尝试恢复
            if (System.currentTimeMillis() - openTime.get() >= timeout.toMillis()) {
                log.info("[RPC] 熔断器进入半开状态: service={}", serviceName);
                state = State.HALF_OPEN;
                successCount.set(0);
                return true;
            }
            return false;
        }

        // HALF_OPEN状态，允许部分请求通过
        return true;
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            int count = successCount.incrementAndGet();
            if (count >= successThreshold) {
                log.info("[RPC] 熔断器关闭: service={}", serviceName);
                state = State.CLOSED;
                failureCount.set(0);
                successCount.set(0);
            }
        } else if (state == State.CLOSED) {
            // 重置失败计数
            failureCount.set(0);
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        int count = failureCount.incrementAndGet();

        if (state == State.HALF_OPEN) {
            // 半开状态下失败，立即打开
            log.warn("[RPC] 熔断器重新打开: service={}", serviceName);
            state = State.OPEN;
            openTime.set(System.currentTimeMillis());
            successCount.set(0);
        } else if (state == State.CLOSED && count >= failureThreshold) {
            // 关闭状态下失败次数达到阈值，打开熔断器
            log.error("[RPC] 熔断器打开: service={}, failureCount={}", serviceName, count);
            state = State.OPEN;
            openTime.set(System.currentTimeMillis());
        }
    }

    /**
     * 重置熔断器
     */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        openTime.set(0);
        log.info("[RPC] 熔断器已重置: service={}", serviceName);
    }

    /**
     * 是否打开
     */
    public boolean isOpen() {
        return state == State.OPEN;
    }

    /**
     * 是否关闭
     */
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    /**
     * 是否半开
     */
    public boolean isHalfOpen() {
        return state == State.HALF_OPEN;
    }
}
