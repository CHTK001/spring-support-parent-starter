package com.chua.starter.oauth.client.support.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * o认证客户端弹性处理器
 * <p>
 * 提供熔断降级、重试机制，增强客户端的容错能力。
 * 基于滑动窗口实现简单的熔断器。
 * </p>
 *
 * @since 2024/12/07
 * @author CH
 */
@Slf4j
public class OAuthClientResilience {

    /**
     * 重试配置
    */
    private final AuthClientProperties.RetryConfig retryConfig;
    /**
     * Circuitbreaker配置
    */
    private final AuthClientProperties.CircuitBreakerConfig circuitBreakerConfig;

    /**
     * 熔断器状态
     * @author CH
     * @since 4.0.0
     */
    private enum CircuitState {
        CLOSED,      // 关闭状态（正常）
        OPEN,        // 打开状态（熔断中）
        HALF_OPEN    // 半开状态（尝试恢复）
    }

    private volatile CircuitState state = CircuitState.CLOSED;
    /**
     * 失败数量
    */
    private final AtomicInteger failureCount = new AtomicInteger(0);
    /**
     * 成功数量
    */
    private final AtomicInteger successCount = new AtomicInteger(0);
    /**
     * 请求数量
    */
    private final AtomicInteger requestCount = new AtomicInteger(0);
    /**
     * 最后失败时间
    */
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    /**
     * Halfopen请求数量
    */
    private final AtomicInteger halfOpenRequestCount = new AtomicInteger(0);

    /**
     * 创建 o认证客户端韧性 实例
     * @param authClientProperties 认证客户端属性
     * @return o认证客户端韧性的结果
     */
    public OAuthClientResilience(AuthClientProperties authClientProperties) {
        this.retryConfig = authClientProperties.getRetry();
        this.circuitBreakerConfig = authClientProperties.getCircuitBreaker();
        log.info("[弹性处理器]初始化完成 - 重试启用: {}, 熔断启用: {}", 
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
            log.warn("[熔断器]熔断器处于打开状态，执行降级逻辑");
            return fallback.get();
        }

        // 执行带重试的请求
        AuthenticationInformation result = executeWithRetry(supplier);

        // 更新熔断器状态
        if (circuitBreakerConfig.isEnabled()) {
            /**
             * record结果。
             * @param result 结果
             */
            recordResult(result);
        }

        return result;
    }

    /**
     * 带重试的执行
     * @param supplier 供应商
     * @return 执行with重试的结果
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
 // 注意：认证_服务端_异常 且 错误消息 不为空，说明是业务错误（如密码错误），不应重试
                if (result != null && result.getInformation() != Information.AUTHENTICATION_SERVER_EXCEPTION) {
                    if (attempt > 1) {
                        log.info("[重试机制]第{}次重试成功", attempt);
                    }
                    return result;
                }
                // 业务错误（服务端返回了明确的错误消息），不重试
                if (result != null && result.getErrorMessage() != null) {
                    return result;
                }

                // 服务器网络异常，需要重试
                if (attempt < maxAttempts) {
                    log.warn("[重试机制]第{}次请求失败，{}ms后重试 (共{}次)", attempt, delay, maxAttempts);
                    Thread.sleep(delay);
                    // 指数退避
                    delay = Math.min((long) (delay * retryConfig.getMultiplier()), retryConfig.getMaxDelay());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[重试机制]重试被中断");
                break;
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("[重试机制]第{}次请求异常: {}, {}ms后重试", attempt, e.getMessage(), delay);
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

        log.error("[重试机制]达到最大重试次数({})，请求失败", maxAttempts);
        if (lastException != null) {
            log.error("[重试机制]最后一次异常: {}", lastException.getMessage());
        }
        return AuthenticationInformation.authServerError();
    }

    /**
     * 检查熔断器是否打开
     * @return 是否熔断打开的结果
     */
    private boolean isCircuitOpen() {
        if (state == CircuitState.CLOSED) {
            return false;
        }

        if (state == CircuitState.OPEN) {
            // [P3C A20 豁免]
            long waitDuration = circuitBreakerConfig.getWaitDurationInOpenState() * 1000L;
            if (System.currentTimeMillis() - lastFailureTime.get() > waitDuration) {
                // 进入半开状态
                state = CircuitState.HALF_OPEN;
                halfOpenRequestCount.set(0);
                log.info("[熔断器]进入半开状态，开始尝试恢复");
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
     * @param result 结果
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
                    /**
                     * reset。
                     */
                    reset();
                    log.info("[熔断器]服务恢复，熔断器关闭");
                }
            }
        } else {
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            if (state == CircuitState.HALF_OPEN) {
                // 半开状态失败，重新打开熔断器
                state = CircuitState.OPEN;
                log.warn("[熔断器]半开状态请求失败，重新打开熔断器");
            } else if (state == CircuitState.CLOSED) {
                // 检查是否需要打开熔断器
                int windowSize = circuitBreakerConfig.getSlidingWindowSize();
                if (requestCount.get() >= windowSize) {
                    // [P3C A20 豁免]
                    int failureRate = (failureCount.get() * 100) / requestCount.get();
                    if (failureRate >= circuitBreakerConfig.getFailureRateThreshold()) {
                        state = CircuitState.OPEN;
                        log.warn("[熔断器]失败率达到阈值({}%)，熔断器打开", failureRate);
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
     * @return 获取熔断状态的结果
     */
    public String getCircuitState() {
        return state.name();
    }

    /**
     * 获取统计信息
     * @return 获取stats的结果
     */
    public String getStats() {
        return String.format("state=%s, requests=%d, failures=%d, successes=%d",
                state, requestCount.get(), failureCount.get(), successCount.get());
    }

    /**
     * 创建降级响应
     * @param message 消息
     * @return 创建降级响应的结果
     */
    public static AuthenticationInformation createFallbackResponse(String message) {
        log.warn("[降级处理]返回降级响应: {}", message);
        /**
         * 认证信息。
         * @param Information.AUTHENTICATION_SERVER_EXCEPTION 信息.认证服务端异常
         * @param null 空
         * @return 认证信息的结果
         */
        return new AuthenticationInformation(Information.AUTHENTICATION_SERVER_EXCEPTION, null);
    }
}
