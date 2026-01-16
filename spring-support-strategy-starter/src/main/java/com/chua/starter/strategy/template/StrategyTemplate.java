package com.chua.starter.strategy.template;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 策略模板接口
 * <p>
 * 提供统一的策略管理和执行API，支持限流、熔断、防抖等策略的动态注册和执行。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-25
 */
public interface StrategyTemplate {

    // ========== 限流策略 ==========

    /**
     * 注册限流策略
     *
     * @param name   策略名称
     * @param config 限流配置
     */
    void registerRateLimiter(String name, RateLimiterStrategyConfig config);

    /**
     * 更新限流策略
     *
     * @param name   策略名称
     * @param config 限流配置
     */
    void updateRateLimiter(String name, RateLimiterStrategyConfig config);

    /**
     * 执行限流保护
     *
     * @param name     策略名称
     * @param supplier 执行逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T executeWithRateLimit(String name, Supplier<T> supplier);

    /**
     * 执行限流保护（带降级）
     *
     * @param name     策略名称
     * @param supplier 执行逻辑
     * @param fallback 降级逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T executeWithRateLimit(String name, Supplier<T> supplier, Supplier<T> fallback);

    // ========== 熔断策略 ==========

    /**
     * 注册熔断策略
     *
     * @param name   策略名称
     * @param config 熔断配置
     */
    void registerCircuitBreaker(String name, CircuitBreakerStrategyConfig config);

    /**
     * 更新熔断策略
     *
     * @param name   策略名称
     * @param config 熔断配置
     */
    void updateCircuitBreaker(String name, CircuitBreakerStrategyConfig config);

    /**
     * 执行熔断保护
     *
     * @param name     策略名称
     * @param supplier 执行逻辑
     * @param fallback 降级逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier, Function<Throwable, T> fallback);

    // ========== 防抖策略 ==========

    /**
     * 注册防抖策略
     *
     * @param name     策略名称
     * @param duration 防抖时间间隔
     */
    void registerDebounce(String name, Duration duration);

    /**
     * 执行防抖
     *
     * @param name   策略名称
     * @param key    防抖键（用于区分不同的防抖实例）
     * @param action 执行动作
     * @return 是否执行成功（false表示被防抖拦截）
     */
    boolean executeWithDebounce(String name, String key, Runnable action);

    // ========== 重试策略 ==========

    /**
     * 注册重试策略
     *
     * @param name   策略名称
     * @param config 重试配置
     */
    void registerRetry(String name, RetryStrategyConfig config);

    /**
     * 执行重试
     *
     * @param name     策略名称
     * @param supplier 执行逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T executeWithRetry(String name, Supplier<T> supplier);

    // ========== 策略链 ==========

    /**
     * 创建策略链
     *
     * @param strategies 策略名称列表
     * @return 策略链
     */
    StrategyChain chain(String... strategies);

    // ========== 管理接口 ==========

    /**
     * 获取所有策略信息
     *
     * @return 策略信息映射
     */
    Map<String, StrategyInfo> getAllStrategies();

    /**
     * 获取指定策略信息
     *
     * @param name 策略名称
     * @return 策略信息
     */
    StrategyInfo getStrategy(String name);

    /**
     * 检查策略是否存在
     *
     * @param name 策略名称
     * @return 是否存在
     */
    boolean exists(String name);

    /**
     * 移除策略
     *
     * @param name 策略名称
     */
    void remove(String name);

    /**
     * 清空所有策略
     */
    void clear();
}
