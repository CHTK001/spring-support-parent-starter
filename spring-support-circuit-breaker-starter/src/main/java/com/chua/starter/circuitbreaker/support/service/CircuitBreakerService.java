package com.chua.starter.circuitbreaker.support.service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 熔断降级服务接口
 * 
 * 提供熔断器、重试、限流、舱壁隔离、超时控制等功能的统一服务接口。
 * 支持同步和异步两种调用方式，提供编程式的容错解决方案。
 * 
 * @author CH
 * @since 2024/12/20
 */
public interface CircuitBreakerService {

    /**
     * 获取熔断器实例
     * 
     * @param name 实例名称
     * @return CircuitBreaker
     */
    CircuitBreaker getCircuitBreaker(String name);

    /**
     * 获取重试实例
     * 
     * @param name 实例名称
     * @return Retry
     */
    Retry getRetry(String name);

    /**
     * 获取限流器实例
     * 
     * @param name 实例名称
     * @return RateLimiter
     */
    RateLimiter getRateLimiter(String name);

    /**
     * 获取舱壁隔离实例
     * 
     * @param name 实例名称
     * @return Bulkhead
     */
    Bulkhead getBulkhead(String name);

    /**
     * 获取超时控制实例
     * 
     * @param name 实例名称
     * @return TimeLimiter
     */
    TimeLimiter getTimeLimiter(String name);

    /**
     * 执行带熔断器的操作
     * 
     * @param name 熔断器名称
     * @param supplier 操作供应商
     * @param fallback 降级处理
     * @param <T> 返回类型
     * @return 执行结果
     */
    <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 执行带重试的操作
     * 
     * @param name 重试名称
     * @param supplier 操作供应商
     * @param <T> 返回类型
     * @return 执行结果
     */
    <T> T executeWithRetry(String name, Supplier<T> supplier);

    /**
     * 执行带限流的操作
     * 
     * @param name 限流器名称
     * @param supplier 操作供应商
     * @param <T> 返回类型
     * @return 执行结果
     */
    <T> T executeWithRateLimit(String name, Supplier<T> supplier);

    /**
     * 执行带舱壁隔离的操作
     * 
     * @param name 舱壁隔离名称
     * @param supplier 操作供应商
     * @param <T> 返回类型
     * @return 执行结果
     */
    <T> T executeWithBulkhead(String name, Supplier<T> supplier);

    /**
     * 执行带超时控制的异步操作
     * 
     * @param name 超时控制名称
     * @param supplier 异步操作供应商
     * @param <T> 返回类型
     * @return CompletionStage
     */
    <T> CompletionStage<T> executeWithTimeLimit(String name, Supplier<CompletionStage<T>> supplier);

    /**
     * 执行组合容错操作（熔断器 + 重试 + 限流）
     * 
     * @param circuitBreakerName 熔断器名称
     * @param retryName 重试名称
     * @param rateLimiterName 限流器名称
     * @param supplier 操作供应商
     * @param fallback 降级处理
     * @param <T> 返回类型
     * @return 执行结果
     */
    <T> T executeWithCombined(String circuitBreakerName, String retryName, String rateLimiterName, 
                             Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 执行完整的容错操作（所有功能组合）
     * 
     * @param circuitBreakerName 熔断器名称
     * @param retryName 重试名称
     * @param rateLimiterName 限流器名称
     * @param bulkheadName 舱壁隔离名称
     * @param timeLimiterName 超时控制名称
     * @param supplier 异步操作供应商
     * @param fallback 降级处理
     * @param <T> 返回类型
     * @return CompletionStage
     */
    <T> CompletionStage<T> executeWithFullProtection(String circuitBreakerName, String retryName, 
                                                     String rateLimiterName, String bulkheadName, 
                                                     String timeLimiterName, Supplier<CompletionStage<T>> supplier, 
                                                     Supplier<T> fallback);

    /**
     * 获取熔断器状态信息
     * 
     * @param name 熔断器名称
     * @return 状态信息
     */
    String getCircuitBreakerState(String name);

    /**
     * 获取限流器状态信息
     * 
     * @param name 限流器名称
     * @return 状态信息
     */
    String getRateLimiterState(String name);

    /**
     * 获取舱壁隔离状态信息
     * 
     * @param name 舱壁隔离名称
     * @return 状态信息
     */
    String getBulkheadState(String name);

    /**
     * 重置熔断器状态
     * 
     * @param name 熔断器名称
     */
    void resetCircuitBreaker(String name);

    /**
     * 强制打开熔断器
     * 
     * @param name 熔断器名称
     */
    void forceOpenCircuitBreaker(String name);

    /**
     * 强制关闭熔断器
     * 
     * @param name 熔断器名称
     */
    void forceCloseCircuitBreaker(String name);
}
