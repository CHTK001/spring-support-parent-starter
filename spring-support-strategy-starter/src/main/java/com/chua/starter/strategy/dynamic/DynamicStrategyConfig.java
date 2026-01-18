package com.chua.starter.strategy.dynamic;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态策略配置管理器
 * <p>
 * 支持运行时动态修改各种策略的配置，包括：
 * - 熔断器配置
 * - 限流器配置
 * - 重试配置
 * - 超时配置
 * - 舱壁配置
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicStrategyConfig {

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired(required = false)
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired(required = false)
    private RetryRegistry retryRegistry;

    @Autowired(required = false)
    private TimeLimiterRegistry timeLimiterRegistry;

    @Autowired(required = false)
    private BulkheadRegistry bulkheadRegistry;

    /**
     * 配置变更监听器
     */
    private final Map<String, ConfigChangeListener> listeners = new ConcurrentHashMap<>();

    /**
     * 更新熔断器配置
     *
     * @param name                  熔断器名称
     * @param failureRateThreshold  失败率阈值
     * @param waitDurationInOpenState 开启状态等待时间（秒）
     * @param slidingWindowSize     滑动窗口大小
     */
    public void updateCircuitBreakerConfig(String name, float failureRateThreshold,
                                            long waitDurationInOpenState, int slidingWindowSize) {
        if (circuitBreakerRegistry == null) {
            log.warn("CircuitBreakerRegistry未配置");
            return;
        }

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenState))
                .slidingWindowSize(slidingWindowSize)
                .build();

        // 移除旧的熔断器，创建新配置的熔断器
        circuitBreakerRegistry.remove(name);
        circuitBreakerRegistry.circuitBreaker(name, config);

        log.info("动态更新熔断器配置: name={}, failureRate={}, waitDuration={}s, windowSize={}",
                name, failureRateThreshold, waitDurationInOpenState, slidingWindowSize);

        notifyListeners(name, "CIRCUIT_BREAKER", config);
    }

    /**
     * 更新限流器配置
     *
     * @param name              限流器名称
     * @param limitForPeriod    周期内允许的请求数
     * @param limitRefreshPeriod 刷新周期（毫秒）
     * @param timeoutDuration   超时时间（毫秒）
     */
    public void updateRateLimiterConfig(String name, int limitForPeriod,
                                         long limitRefreshPeriod, long timeoutDuration) {
        if (rateLimiterRegistry == null) {
            log.warn("RateLimiterRegistry未配置");
            return;
        }

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(Duration.ofMillis(limitRefreshPeriod))
                .timeoutDuration(Duration.ofMillis(timeoutDuration))
                .build();

        rateLimiterRegistry.remove(name);
        rateLimiterRegistry.rateLimiter(name, config);

        log.info("动态更新限流器配置: name={}, limit={}, refreshPeriod={}ms, timeout={}ms",
                name, limitForPeriod, limitRefreshPeriod, timeoutDuration);

        notifyListeners(name, "RATE_LIMITER", config);
    }

    /**
     * 更新重试配置
     *
     * @param name             重试器名称
     * @param maxAttempts      最大重试次数
     * @param waitDuration     等待时间（毫秒）
     * @param retryExceptions  需要重试的异常类型
     */
    @SafeVarargs
    public final void updateRetryConfig(String name, int maxAttempts, long waitDuration,
                                         Class<? extends Throwable>... retryExceptions) {
        if (retryRegistry == null) {
            log.warn("RetryRegistry未配置");
            return;
        }

        RetryConfig.Builder<?> builder = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(waitDuration));

        if (retryExceptions != null && retryExceptions.length > 0) {
            builder.retryExceptions(retryExceptions);
        }

        RetryConfig config = builder.build();

        retryRegistry.remove(name);
        retryRegistry.retry(name, config);

        log.info("动态更新重试配置: name={}, maxAttempts={}, waitDuration={}ms",
                name, maxAttempts, waitDuration);

        notifyListeners(name, "RETRY", config);
    }

    /**
     * 更新超时配置
     *
     * @param name            超时器名称
     * @param timeoutDuration 超时时间（毫秒）
     */
    public void updateTimeLimiterConfig(String name, long timeoutDuration) {
        if (timeLimiterRegistry == null) {
            log.warn("TimeLimiterRegistry未配置");
            return;
        }

        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutDuration))
                .build();

        timeLimiterRegistry.remove(name);
        timeLimiterRegistry.timeLimiter(name, config);

        log.info("动态更新超时配置: name={}, timeout={}ms", name, timeoutDuration);

        notifyListeners(name, "TIME_LIMITER", config);
    }

    /**
     * 更新舱壁配置
     *
     * @param name               舱壁名称
     * @param maxConcurrentCalls 最大并发数
     * @param maxWaitDuration    最大等待时间（毫秒）
     */
    public void updateBulkheadConfig(String name, int maxConcurrentCalls, long maxWaitDuration) {
        if (bulkheadRegistry == null) {
            log.warn("BulkheadRegistry未配置");
            return;
        }

        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ofMillis(maxWaitDuration))
                .build();

        bulkheadRegistry.remove(name);
        bulkheadRegistry.bulkhead(name, config);

        log.info("动态更新舱壁配置: name={}, maxConcurrent={}, maxWait={}ms",
                name, maxConcurrentCalls, maxWaitDuration);

        notifyListeners(name, "BULKHEAD", config);
    }

    /**
     * 注册配置变更监听器
     *
     * @param listenerId 监听器ID
     * @param listener   监听器
     */
    public void registerListener(String listenerId, ConfigChangeListener listener) {
        listeners.put(listenerId, listener);
    }

    /**
     * 移除配置变更监听器
     *
     * @param listenerId 监听器ID
     */
    public void removeListener(String listenerId) {
        listeners.remove(listenerId);
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(String configName, String configType, Object newConfig) {
        listeners.values().forEach(listener -> {
            try {
                listener.onConfigChange(configName, configType, newConfig);
            } catch (Exception e) {
                log.error("配置变更通知失败: listener={}", listener, e);
            }
        });
    }

    /**
     * 获取熔断器状态
     *
     * @param name 熔断器名称
     * @return 状态信息
     */
    public String getCircuitBreakerState(String name) {
        if (circuitBreakerRegistry == null) {
            return "UNKNOWN";
        }
        return circuitBreakerRegistry.find(name)
                .map(cb -> cb.getState().name())
                .orElse("NOT_FOUND");
    }

    /**
     * 手动切换熔断器状态
     *
     * @param name  熔断器名称
     * @param state 目标状态：CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN
     */
    public void transitionCircuitBreakerState(String name, String state) {
        if (circuitBreakerRegistry == null) {
            return;
        }

        circuitBreakerRegistry.find(name).ifPresent(cb -> {
            switch (state.toUpperCase()) {
                case "CLOSED" -> cb.transitionToClosedState();
                case "OPEN" -> cb.transitionToOpenState();
                case "HALF_OPEN" -> cb.transitionToHalfOpenState();
                case "DISABLED" -> cb.transitionToDisabledState();
                case "FORCED_OPEN" -> cb.transitionToForcedOpenState();
                default -> log.warn("未知的熔断器状态: {}", state);
            }
            log.info("手动切换熔断器状态: name={}, state={}", name, state);
        });
    }

    /**
     * 配置变更监听器接口
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        /**
         * 配置变更回调
         *
         * @param configName 配置名称
         * @param configType 配置类型
         * @param newConfig  新配置
         */
        void onConfigChange(String configName, String configType, Object newConfig);
    }
}
