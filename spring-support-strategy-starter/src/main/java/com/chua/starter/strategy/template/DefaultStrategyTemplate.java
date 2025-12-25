package com.chua.starter.strategy.template;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 默认策略模板实现
 * <p>
 * 基于 Resilience4j 实现限流、熔断、重试等策略
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
public class DefaultStrategyTemplate implements StrategyTemplate {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    private final Map<String, StrategyInfo> strategyInfoMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> debounceMap = new ConcurrentHashMap<>();
    private final Map<String, Duration> debounceDurations = new ConcurrentHashMap<>();

    public DefaultStrategyTemplate() {
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
    }

    public DefaultStrategyTemplate(RateLimiterRegistry rateLimiterRegistry,
                                   CircuitBreakerRegistry circuitBreakerRegistry,
                                   RetryRegistry retryRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry != null ? rateLimiterRegistry : RateLimiterRegistry.ofDefaults();
        this.circuitBreakerRegistry = circuitBreakerRegistry != null ? circuitBreakerRegistry : CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = retryRegistry != null ? retryRegistry : RetryRegistry.ofDefaults();
    }

    // ========== 限流策略 ==========

    @Override
    public void registerRateLimiter(String name, RateLimiterStrategyConfig config) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(config.getLimitForPeriod())
                .limitRefreshPeriod(config.getLimitRefreshPeriod())
                .timeoutDuration(config.getTimeoutDuration())
                .build();

        rateLimiterRegistry.rateLimiter(name, rateLimiterConfig);

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
        rateLimiterRegistry.remove(name);
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
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(name);
        return RateLimiter.decorateSupplier(rateLimiter, supplier).get();
    }

    @Override
    public <T> T executeWithRateLimit(String name, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            return executeWithRateLimit(name, supplier);
        } catch (Exception e) {
            log.warn("Rate limit exceeded for {}, executing fallback", name);
            return fallback.get();
        }
    }

    // ========== 熔断策略 ==========

    @Override
    public void registerCircuitBreaker(String name, CircuitBreakerStrategyConfig config) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.getFailureRateThreshold())
                .slowCallRateThreshold(config.getSlowCallRateThreshold())
                .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(config.getSlidingWindowSize())
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                .waitDurationInOpenState(config.getWaitDurationInOpenState())
                .build();

        circuitBreakerRegistry.circuitBreaker(name, circuitBreakerConfig);

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
        circuitBreakerRegistry.remove(name);
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
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, supplier).get();
        } catch (Exception e) {
            log.warn("Circuit breaker {} triggered, executing fallback", name);
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
        RetryConfig.Builder<Object> builder = RetryConfig.custom()
                .maxAttempts(config.getMaxAttempts())
                .waitDuration(config.getWaitDuration());

        if (!config.getRetryExceptions().isEmpty()) {
            builder.retryExceptions(config.getRetryExceptions().toArray(new Class[0]));
        }

        if (!config.getIgnoreExceptions().isEmpty()) {
            builder.ignoreExceptions(config.getIgnoreExceptions().toArray(new Class[0]));
        }

        if (config.isExponentialBackoff()) {
            builder.intervalFunction(attempt -> 
                    (long) (config.getWaitDuration().toMillis() * Math.pow(config.getExponentialBackoffMultiplier(), attempt - 1)));
        }

        retryRegistry.retry(name, builder.build());

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
        Retry retry = retryRegistry.retry(name);
        return Retry.decorateSupplier(retry, supplier).get();
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
            case RATE_LIMITER -> rateLimiterRegistry.remove(name);
            case CIRCUIT_BREAKER -> circuitBreakerRegistry.remove(name);
            case RETRY -> retryRegistry.remove(name);
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
