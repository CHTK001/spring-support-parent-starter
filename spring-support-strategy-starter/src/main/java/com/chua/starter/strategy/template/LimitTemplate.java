package com.chua.starter.strategy.template;

import java.util.function.Supplier;

/**
 * 限流模板接口
 * <p>
 * 提供统一的限流API，支持多种限流器实现（如本地、Redis等）。
 * 通过 SPI 机制自动发现和加载不同的限流器实现。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public interface LimitTemplate {

    /**
     * 尝试获取限流许可并执行
     * <p>
     * 如果获取许可成功，执行业务逻辑并返回结果。
     * 如果获取许可失败（被限流），返回null。
     * </p>
     *
     * @param key            限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds  周期时间（秒）
     * @param supplier       执行逻辑
     * @param <T>            返回类型
     * @return 执行结果，如果被限流则返回null
     */
    <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, Supplier<T> supplier);

    /**
     * 尝试获取限流许可并执行（带超时）
     *
     * @param key             限流键
     * @param limitForPeriod  周期内允许的请求数
     * @param periodSeconds   周期时间（秒）
     * @param timeoutMillis   超时时间（毫秒）
     * @param supplier        执行逻辑
     * @param <T>             返回类型
     * @return 执行结果，如果被限流或超时则返回null
     */
    <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis, Supplier<T> supplier);

    /**
     * 尝试获取限流许可并执行（带降级）
     *
     * @param key            限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds  周期时间（秒）
     * @param supplier       执行逻辑
     * @param fallback        降级逻辑（被限流时执行）
     * @param <T>             返回类型
     * @return 执行结果
     */
    <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 尝试获取限流许可并执行（带超时和降级）
     *
     * @param key             限流键
     * @param limitForPeriod  周期内允许的请求数
     * @param periodSeconds   周期时间（秒）
     * @param timeoutMillis   超时时间（毫秒）
     * @param supplier        执行逻辑
     * @param fallback         降级逻辑（被限流或超时时执行）
     * @param <T>              返回类型
     * @return 执行结果
     */
    <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis, Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 尝试获取限流许可并执行（无返回值）
     *
     * @param key            限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds  周期时间（秒）
     * @param action         执行动作
     * @return 是否执行成功
     */
    boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, Runnable action);

    /**
     * 尝试获取限流许可并执行（带超时，无返回值）
     *
     * @param key             限流键
     * @param limitForPeriod  周期内允许的请求数
     * @param periodSeconds   周期时间（秒）
     * @param timeoutMillis   超时时间（毫秒）
     * @param action          执行动作
     * @return 是否执行成功
     */
    boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis, Runnable action);

    /**
     * 获取当前剩余许可数
     *
     * @param key            限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds  周期时间（秒）
     * @return 剩余许可数
     */
    long getAvailablePermits(String key, int limitForPeriod, int periodSeconds);

    /**
     * 重置限流计数
     *
     * @param key 限流键
     */
    void reset(String key);

    /**
     * 检查是否被限流
     *
     * @param key            限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds  周期时间（秒）
     * @return true-被限流，false-未被限流
     */
    boolean isLimited(String key, int limitForPeriod, int periodSeconds);
}

