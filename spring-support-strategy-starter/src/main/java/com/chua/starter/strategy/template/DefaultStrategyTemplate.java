package com.chua.starter.strategy.template;

import com.chua.common.support.resilience.rate.RateLimiterFlow;
import com.chua.common.support.resilience.rate.RateLimiterProvider;
import com.chua.common.support.resilience.rate.RateLimiterSetting;
import com.chua.common.support.resilience.retry.RetryProvider;
import com.chua.common.support.resilience.retry.RetrySetting;
import com.chua.common.support.resilience.retry.RetryerFlow;
import com.chua.common.support.task.resilience.ResilienceFlow;
import com.chua.common.support.task.resilience.ResilienceProvider;
import com.chua.common.support.task.resilience.ResilienceSetting;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 默认策略模板实现
 * <p>
 * 基于 utils-parent 下的模块实现限流、熔断、重试等策略
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
public class DefaultStrategyTemplate implements StrategyTemplate {

    private final Map<String, RateLimiterProvider> rateLimiterMap = new ConcurrentHashMap<>();
    private final Map<String, ResilienceProvider> circuitBreakerMap = new ConcurrentHashMap<>();
    private final Map<String, RetryProvider> retryMap = new ConcurrentHashMap<>();
    private final Map<String, StrategyInfo> strategyInfoMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> debounceMap = new ConcurrentHashMap<>();
    private final Map<String, Duration> debounceDurations = new ConcurrentHashMap<>();

    /**
     * 默认限流器类型
     */
    private static final String DEFAULT_RATE_LIMITER_TYPE = "resilience4j";

    /**
     * 默认熔断器类型
     */
    private static final String DEFAULT_RESILIENCE_TYPE = "resilience4j";

    /**
     * 默认重试器类型
     */
    private static final String DEFAULT_RETRY_TYPE = "resilience4j";

    // ========== 限流策略 ==========

    @Override
    public void registerRateLimiter(String name, RateLimiterStrategyConfig config) {
        RateLimiterSetting setting = RateLimiterSetting.builder()
                .name(name)
                .permitsPerSecond(config.getLimitForPeriod())
                .limitRefreshPeriod(config.getLimitRefreshPeriod().toMillis())
                .timeoutDuration(config.getTimeoutDuration().toMillis())
                .rateLimiterType(DEFAULT_RATE_LIMITER_TYPE)
                .build();

        RateLimiterProvider provider = RateLimiterFlow.createRateLimiter(name, setting);
        rateLimiterMap.put(name, provider);

        strategyInfoMap.put(name, StrategyInfo.builder()
                .name(name)
                .type(StrategyInfo.StrategyType.RATE_LIMITER)
                .config(config)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());

        log.info("Registered rate limiter strategy: {}", name);
    }

    @Override
    public void updateRateLimiter(String name, RateLimiterStrategyConfig config) {
        rateLimiterMap.remove(name);
        registerRateLimiter(name, config);

        StrategyInfo info = strategyInfoMap.get(name);
        if (info != null) {
            info.setConfig(config);
            info.setUpdateTime(LocalDateTime.now());
        }

        log.info("Updated rate limiter strategy: {}", name);
    }

    @Override
    public <T> T executeWithRateLimit(String name, Supplier<T> supplier) {
        RateLimiterProvider provider = rateLimiterMap.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Rate limiter not found: " + name);
        }

        if (!provider.tryAcquire()) {
            throw new RuntimeException("Rate limit exceeded for: " + name);
        }

        return supplier.get();
    }

    @Override
    public <T> T executeWithRateLimit(String name, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            return executeWithRateLimit(name, supplier);
        } catch (Exception e) {
            log.warn("Rate limit exceeded for {}, executing fallback", name, e);
            return fallback.get();
        }
    }

    // ========== 熔断策略 ==========

    @Override
    public void registerCircuitBreaker(String name, CircuitBreakerStrategyConfig config) {
        ResilienceSetting setting = ResilienceSetting.builder()
                .name(name)
                .failureRateThreshold((int) config.getFailureRateThreshold())
                .slowCallRateThreshold((int) config.getSlowCallRateThreshold())
                .slowCallDurationThreshold(config.getSlowCallDurationThreshold().toMillis())
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(config.getSlidingWindowSize())
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                .waitDurationInOpenState(config.getWaitDurationInOpenState().toMillis())
                .resilienceType(DEFAULT_RESILIENCE_TYPE)
                .build();

        ResilienceProvider provider = ResilienceFlow.createResilience(name, setting);
        circuitBreakerMap.put(name, provider);

        strategyInfoMap.put(name, StrategyInfo.builder()
                .name(name)
                .type(StrategyInfo.StrategyType.CIRCUIT_BREAKER)
                .config(config)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());

        log.info("Registered circuit breaker strategy: {}", name);
    }

    @Override
    public void updateCircuitBreaker(String name, CircuitBreakerStrategyConfig config) {
        circuitBreakerMap.remove(name);
        registerCircuitBreaker(name, config);

        StrategyInfo info = strategyInfoMap.get(name);
        if (info != null) {
            info.setConfig(config);
            info.setUpdateTime(LocalDateTime.now());
        }

        log.info("Updated circuit breaker strategy: {}", name);
    }

    @Override
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier, Function<Throwable, T> fallback) {
        ResilienceProvider provider = circuitBreakerMap.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Circuit breaker not found: " + name);
        }

        try {
            return provider.execute(supplier);
        } catch (Throwable e) {
            log.warn("Circuit breaker {} triggered, executing fallback", name, e);
            return fallback.apply(e);
        }
    }

    // ========== 防抖策略 ==========

    @Override
    public void registerDebounce(String name, Duration duration) {
        debounceDurations.put(name, duration);
        debounceMap.put(name, new ConcurrentHashMap<>());

        strategyInfoMap.put(name, StrategyInfo.builder()
                .name(name)
                .type(StrategyInfo.StrategyType.DEBOUNCE)
                .config(duration)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());

        log.info("Registered debounce strategy: {} with duration: {}", name, duration);
    }

    @Override
    public boolean executeWithDebounce(String name, String key, Runnable action) {
        Duration duration = debounceDurations.get(name);
        if (duration == null) {
            action.run();
            return true;
        }

        Map<String, Long> keyMap = debounceMap.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long lastExecution = keyMap.get(key);

        if (lastExecution == null || (now - lastExecution) >= duration.toMillis()) {
            keyMap.put(key, now);
            action.run();
            return true;
        }

        log.debug("Debounce {} blocked execution for key: {}", name, key);
        return false;
    }

    // ========== 重试策略 ==========

    @Override
    public void registerRetry(String name, RetryStrategyConfig config) {
        RetrySetting.Builder builder = RetrySetting.builder()
                .maxAttempts(config.getMaxAttempts())
                .waitDuration(config.getWaitDuration());

        if (!config.getRetryExceptions().isEmpty()) {
            builder.retryOnExceptions(config.getRetryExceptions());
        }

        if (!config.getIgnoreExceptions().isEmpty()) {
            builder.ignoreOnExceptions(config.getIgnoreExceptions());
        }

        RetrySetting setting = builder.build();
        RetryProvider provider = RetryerFlow.create(name, DEFAULT_RETRY_TYPE, setting);
        retryMap.put(name, provider);

        strategyInfoMap.put(name, StrategyInfo.builder()
                .name(name)
                .type(StrategyInfo.StrategyType.RETRY)
                .config(config)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());

        log.info("Registered retry strategy: {}", name);
    }

    @Override
    public <T> T executeWithRetry(String name, Supplier<T> supplier) {
        RetryProvider provider = retryMap.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Retry provider not found: " + name);
        }

        return provider.execute(supplier);
    }

    // ========== 策略链 ==========

    @Override
    public StrategyChain chain(String... strategies) {
        return new DefaultStrategyChain(this, strategies);
    }

    // ========== 管理接口 ==========

    @Override
    public Map<String, StrategyInfo> getAllStrategies() {
        return new ConcurrentHashMap<>(strategyInfoMap);
    }

    @Override
    public StrategyInfo getStrategy(String name) {
        return strategyInfoMap.get(name);
    }

    @Override
    public boolean exists(String name) {
        return strategyInfoMap.containsKey(name);
    }

    @Override
    public void remove(String name) {
        StrategyInfo info = strategyInfoMap.remove(name);
        if (info == null) {
            return;
        }

        switch (info.getType()) {
            case RATE_LIMITER -> rateLimiterMap.remove(name);
            case CIRCUIT_BREAKER -> circuitBreakerMap.remove(name);
            case RETRY -> retryMap.remove(name);
            case DEBOUNCE -> {
                debounceDurations.remove(name);
                debounceMap.remove(name);
            }
        }

        log.info("Removed strategy: {}", name);
    }

    @Override
    public void clear() {
        strategyInfoMap.keySet().forEach(this::remove);
        log.info("Cleared all strategies");
    }
}
