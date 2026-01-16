package com.chua.starter.strategy.template;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 策略链
 * <p>
 * 支持将多个策略组合执行
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
public interface StrategyChain {

    /**
     * 执行策略链
     *
     * @param supplier 执行逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T execute(Supplier<T> supplier);

    /**
     * 执行策略链（带降级）
     *
     * @param supplier 执行逻辑
     * @param fallback 降级逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T execute(Supplier<T> supplier, Function<Throwable, T> fallback);

    /**
     * 执行无返回值的策略链
     *
     * @param action 执行动作
     */
    void execute(Runnable action);

    /**
     * 获取策略链中的策略名称
     *
     * @return 策略名称数组
     */
    String[] getStrategies();
}
