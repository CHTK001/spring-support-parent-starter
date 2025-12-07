package com.chua.starter.oauth.client.support.resilience;

import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * OAuth客户端弹性处理器
 * <p>
 * 提供熔断降级、重试机制，增强客户端的容错能力。
 * 基于滑动窗口实现简单的熔断器。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 */
@Slf4j
public class OAuthClientResilience {

    private final AuthClientProperties.RetryConfig retryConfig;
    private final AuthClientProperties.CircuitBreakerConfig circuitBreakerConfig;

    /**
     * 熔断器状态
     */
    private enum CircuitState {
        CLOSED,      // 关闭状态（正常）
        OPEN,        // 打开状态（熔断中）
        HALF_OPEN    // 半开状态（尝试恢复）
    }

    private volatile CircuitState state = CircuitState.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger halfOpenRequestCount = new AtomicInteger(0);

    public OAuthClientResilience(AuthClientProperties authClientProperties) {
        this.retryConfig = authClientProperties.getRetry();
        this.circuitBreakerConfig = authClientProperties.getCircuitBreaker();
        log.info("【弹性处理器】初始化完成 - 重试启用: {}, 熔断启用: {}", 
                retryConfig.isEnabled(), circuitBreakerConfig.isEnabled());
    }

    /**
     * 带弹性保护的执行
     *
     * @param supplier    认证操作
     * @param fallback    降级响应
     * @return 认证信息
     */
    public AuthenticationInformation executeWithResilience(
            Supplier<AuthenticationInformation> supplier,
            Supplier<AuthenticationInformation> fallback) {
        
        // 检查熔断器状态
        if (circuitBreakerConfig.isEnabled() && isCircuitOpen()) {
            log.warn("【熔断器】熔断器处于打开状态，执行降级逻辑");
            return fallback.get();
        }

        // 执行带重试的请求
        AuthenticationInformation result = executeWithRetry(supplier);

        // 更新熔断器状态
        if (circuitBreakerConfig.isEnabled()) {
            recordResult(result);
        }

        return result;
    }

    /**
     * 带重试的执行
     */
    private AuthenticationInformation executeWithRetry(Supplier<AuthenticationInformation> supplier) {
        if (!retryConfig.isEnabled()) {
            return supplier.get();
        }

        int maxAttempts = retryConfig.getMaxAttempts();
        long delay = retryConfig.getDelay();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                AuthenticationInformation result = supplier.get();
                
                // 如果认证成功或明确失败（非网络错误），不需要重试
                if (result != null && result.getInformation() != Information.AUTHENTICATION_SERVER_EXCEPTION) {
                    if (attempt > 1) {
                        log.info("【重试机制】第{}次重试成功", attempt);
                    }
                    return result;
                }

                // 服务器异常，需要重试
                if (attempt < maxAttempts) {
                    log.warn("【重试机制】第{}次请求失败，{}ms后重试 (共{}次)", attempt, delay, maxAttempts);
                    Thread.sleep(delay);
                    // 指数退避
                    delay = Math.min((long) (delay * retryConfig.getMultiplier()), retryConfig.getMaxDelay());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("【重试机制】重试被中断");
                break;
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("【重试机制】第{}次请求异常: {}, {}ms后重试", attempt, e.getMessage(), delay);
                    try {
                        Thread.sleep(delay);
                        delay = Math.min((long) (delay * retryConfig.getMultiplier()), retryConfig.getMaxDelay());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("【重试机制】达到最大重试次数({})，请求失败", maxAttempts);
        if (lastException != null) {
            log.error("【重试机制】最后一次异常: {}", lastException.getMessage());
        }
        return AuthenticationInformation.authServerError();
    }

    /**
     * 检查熔断器是否打开
     */
    private boolean isCircuitOpen() {
        if (state == CircuitState.CLOSED) {
            return false;
        }

        if (state == CircuitState.OPEN) {
            long waitDuration = circuitBreakerConfig.getWaitDurationInOpenState() * 1000L;
            if (System.currentTimeMillis() - lastFailureTime.get() > waitDuration) {
                // 进入半开状态
                state = CircuitState.HALF_OPEN;
                halfOpenRequestCount.set(0);
                log.info("【熔断器】进入半开状态，开始尝试恢复");
                return false;
            }
            return true;
        }

        // 半开状态：允许部分请求通过
        if (state == CircuitState.HALF_OPEN) {
            int count = halfOpenRequestCount.incrementAndGet();
            return count > circuitBreakerConfig.getPermittedCallsInHalfOpenState();
        }

        return false;
    }

    /**
     * 记录请求结果，更新熔断器状态
     */
    private void recordResult(AuthenticationInformation result) {
        requestCount.incrementAndGet();

        boolean isSuccess = result != null && 
                result.getInformation() != Information.AUTHENTICATION_SERVER_EXCEPTION &&
                result.getInformation() != null;

        if (isSuccess) {
            successCount.incrementAndGet();
            
            if (state == CircuitState.HALF_OPEN) {
                // 半开状态成功，恢复关闭状态
                if (successCount.get() >= circuitBreakerConfig.getPermittedCallsInHalfOpenState()) {
                    reset();
                    log.info("【熔断器】服务恢复，熔断器关闭");
                }
            }
        } else {
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            if (state == CircuitState.HALF_OPEN) {
                // 半开状态失败，重新打开熔断器
                state = CircuitState.OPEN;
                log.warn("【熔断器】半开状态请求失败，重新打开熔断器");
            } else if (state == CircuitState.CLOSED) {
                // 检查是否需要打开熔断器
                int windowSize = circuitBreakerConfig.getSlidingWindowSize();
                if (requestCount.get() >= windowSize) {
                    int failureRate = (failureCount.get() * 100) / requestCount.get();
                    if (failureRate >= circuitBreakerConfig.getFailureRateThreshold()) {
                        state = CircuitState.OPEN;
                        log.warn("【熔断器】失败率达到阈值({}%)，熔断器打开", failureRate);
                    }
                    // 重置计数器（滑动窗口）
                    failureCount.set(0);
                    successCount.set(0);
                    requestCount.set(0);
                }
            }
        }
    }

    /**
     * 重置熔断器状态
     */
    private void reset() {
        state = CircuitState.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        requestCount.set(0);
        halfOpenRequestCount.set(0);
    }

    /**
     * 获取当前熔断器状态
     */
    public String getCircuitState() {
        return state.name();
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("state=%s, requests=%d, failures=%d, successes=%d",
                state, requestCount.get(), failureCount.get(), successCount.get());
    }

    /**
     * 创建降级响应
     */
    public static AuthenticationInformation createFallbackResponse(String message) {
        log.warn("【降级处理】返回降级响应: {}", message);
        return new AuthenticationInformation(Information.AUTHENTICATION_SERVER_EXCEPTION, null);
    }
}
