package com.chua.starter.circuitbreaker.support.service.impl;

import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 熔断降级服务实现类
 * 
 * 基于Resilience4j实现的熔断降级服务，提供完整的容错解决方案。
 * 支持熔断器、重试、限流、舱壁隔离、超时控制等功能的组合使用。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerServiceImpl implements CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    @Override
    public CircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakerRegistry.circuitBreaker(name);
    }

    @Override
    public Retry getRetry(String name) {
        return retryRegistry.retry(name);
    }

    @Override
    public RateLimiter getRateLimiter(String name) {
        return rateLimiterRegistry.rateLimiter(name);
    }

    @Override
    public Bulkhead getBulkhead(String name) {
        return bulkheadRegistry.bulkhead(name);
    }

    @Override
    public TimeLimiter getTimeLimiter(String name) {
        return timeLimiterRegistry.timeLimiter(name);
    }

    @Override
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.warn("熔断器 {} 执行失败，执行降级处理: {}", name, e.getMessage());
            return fallback.get();
        }
    }

    @Override
    public <T> T executeWithRetry(String name, Supplier<T> supplier) {
        Retry retry = getRetry(name);
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, supplier);
        return decoratedSupplier.get();
    }

    @Override
    public <T> T executeWithRateLimit(String name, Supplier<T> supplier) {
        RateLimiter rateLimiter = getRateLimiter(name);
        Supplier<T> decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, supplier);
        return decoratedSupplier.get();
    }

    @Override
    public <T> T executeWithBulkhead(String name, Supplier<T> supplier) {
        Bulkhead bulkhead = getBulkhead(name);
        Supplier<T> decoratedSupplier = Bulkhead.decorateSupplier(bulkhead, supplier);
        return decoratedSupplier.get();
    }

    @Override
    public <T> CompletionStage<T> executeWithTimeLimit(String name, Supplier<CompletionStage<T>> supplier) {
        TimeLimiter timeLimiter = getTimeLimiter(name);
        return timeLimiter.executeCompletionStage(() -> supplier.get());
    }

    @Override
    public <T> T executeWithCombined(String circuitBreakerName, String retryName, String rateLimiterName, 
                                    Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(circuitBreakerName);
        Retry retry = getRetry(retryName);
        RateLimiter rateLimiter = getRateLimiter(rateLimiterName);

        // 组合装饰器：限流 -> 重试 -> 熔断器
        Supplier<T> decoratedSupplier = Supplier.class.cast(
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        Retry.decorateSupplier(retry,
                                RateLimiter.decorateSupplier(rateLimiter, supplier))));

        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.warn("组合容错执行失败，执行降级处理: {}", e.getMessage());
            return fallback.get();
        }
    }

    @Override
    public <T> CompletionStage<T> executeWithFullProtection(String circuitBreakerName, String retryName, 
                                                           String rateLimiterName, String bulkheadName, 
                                                           String timeLimiterName, Supplier<CompletionStage<T>> supplier, 
                                                           Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(circuitBreakerName);
        Retry retry = getRetry(retryName);
        RateLimiter rateLimiter = getRateLimiter(rateLimiterName);
        Bulkhead bulkhead = getBulkhead(bulkheadName);
        TimeLimiter timeLimiter = getTimeLimiter(timeLimiterName);

        // 组合装饰器：舱壁隔离 -> 限流 -> 重试 -> 熔断器 -> 超时控制
        Supplier<CompletionStage<T>> decoratedSupplier = () -> {
            Supplier<CompletionStage<T>> bulkheadDecorated = Bulkhead.decorateSupplier(bulkhead, supplier);
            Supplier<CompletionStage<T>> rateLimitDecorated = RateLimiter.decorateSupplier(rateLimiter, bulkheadDecorated);
            Supplier<CompletionStage<T>> retryDecorated = Retry.decorateSupplier(retry, rateLimitDecorated);
            Supplier<CompletionStage<T>> circuitBreakerDecorated = CircuitBreaker.decorateSupplier(circuitBreaker, retryDecorated);
            
            return timeLimiter.executeCompletionStage(circuitBreakerDecorated);
        };

        return decoratedSupplier.get().exceptionally(throwable -> {
            log.warn("完整容错保护执行失败，执行降级处理: {}", throwable.getMessage());
            return fallback.get();
        });
    }

    @Override
    public String getCircuitBreakerState(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        CircuitBreaker.State state = circuitBreaker.getState();
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        
        return String.format("状态: %s, 失败率: %.2f%%, 调用次数: %d, 失败次数: %d, 慢调用次数: %d",
                state.name(),
                metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls());
    }

    @Override
    public String getRateLimiterState(String name) {
        RateLimiter rateLimiter = getRateLimiter(name);
        RateLimiter.Metrics metrics = rateLimiter.getMetrics();
        
        return String.format("可用许可: %d, 等待线程数: %d",
                metrics.getAvailablePermissions(),
                metrics.getNumberOfWaitingThreads());
    }

    @Override
    public String getBulkheadState(String name) {
        Bulkhead bulkhead = getBulkhead(name);
        Bulkhead.Metrics metrics = bulkhead.getMetrics();
        
        return String.format("可用并发调用: %d, 最大并发调用: %d",
                metrics.getAvailableConcurrentCalls(),
                metrics.getMaxAllowedConcurrentCalls());
    }

    @Override
    public void resetCircuitBreaker(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        circuitBreaker.reset();
        log.info("熔断器 {} 已重置", name);
    }

    @Override
    public void forceOpenCircuitBreaker(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        circuitBreaker.transitionToOpenState();
        log.info("熔断器 {} 已强制打开", name);
    }

    @Override
    public void forceCloseCircuitBreaker(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        circuitBreaker.transitionToClosedState();
        log.info("熔断器 {} 已强制关闭", name);
    }
}
