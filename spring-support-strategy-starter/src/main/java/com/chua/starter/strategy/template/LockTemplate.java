package com.chua.starter.strategy.template;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁模板接口
 * <p>
 * 提供统一的分布式锁API，支持多种锁实现（如Redisson、Zookeeper等）。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-25
 */
public interface LockTemplate {

    /**
     * 获取锁并执行
     * <p>
     * 获取锁后执行业务逻辑，执行完毕后自动释放锁。
     * 如果获取锁失败，将抛出异常。
     * </p>
     *
     * @param lockKey  锁的唯一标识
     * @param supplier 执行逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T lock(String lockKey, Supplier<T> supplier);

    /**
     * 获取锁并执行
     *
     * @param lockKey   锁的唯一标识
     * @param leaseTime 锁的持有时间
     * @param supplier  执行逻辑
     * @param <T>       返回类型
     * @return 执行结果
     */
    <T> T lock(String lockKey, Duration leaseTime, Supplier<T> supplier);

    /**
     * 尝试获取锁并执行
     * <p>
     * 尝试在指定时间内获取锁，获取成功后执行业务逻辑。
     * 如果获取锁失败，返回null。
     * </p>
     *
     * @param lockKey  锁的唯一标识
     * @param waitTime 等待时间
     * @param supplier 执行逻辑
     * @param <T>      返回类型
     * @return 执行结果，如果获取锁失败则返回null
     */
    <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier);

    /**
     * 尝试获取锁并执行
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待时间
     * @param leaseTime 锁的持有时间
     * @param supplier  执行逻辑
     * @param <T>       返回类型
     * @return 执行结果，如果获取锁失败则返回null
     */
    <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier);

    /**
     * 尝试获取锁并执行（带降级）
     *
     * @param lockKey  锁的唯一标识
     * @param waitTime 等待时间
     * @param supplier 执行逻辑
     * @param fallback 降级逻辑（获取锁失败时执行）
     * @param <T>      返回类型
     * @return 执行结果
     */
    <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 尝试获取锁并执行（带降级）
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待时间
     * @param leaseTime 锁的持有时间
     * @param supplier  执行逻辑
     * @param fallback  降级逻辑（获取锁失败时执行）
     * @param <T>       返回类型
     * @return 执行结果
     */
    <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 获取锁并执行（无返回值）
     *
     * @param lockKey 锁的唯一标识
     * @param action  执行动作
     */
    void lock(String lockKey, Runnable action);

    /**
     * 获取锁并执行（无返回值）
     *
     * @param lockKey   锁的唯一标识
     * @param leaseTime 锁的持有时间
     * @param action    执行动作
     */
    void lock(String lockKey, Duration leaseTime, Runnable action);

    /**
     * 尝试获取锁并执行（无返回值）
     *
     * @param lockKey  锁的唯一标识
     * @param waitTime 等待时间
     * @param action   执行动作
     * @return 是否执行成功
     */
    boolean tryLock(String lockKey, Duration waitTime, Runnable action);

    /**
     * 尝试获取锁并执行（无返回值）
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待时间
     * @param leaseTime 锁的持有时间
     * @param action    执行动作
     * @return 是否执行成功
     */
    boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable action);

    /**
     * 手动获取锁
     * <p>
     * 注意：使用此方法后必须手动调用 unlock 释放锁
     * </p>
     *
     * @param lockKey 锁的唯一标识
     * @return 是否成功获取锁
     */
    boolean acquire(String lockKey);

    /**
     * 手动获取锁
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待时间
     * @param leaseTime 锁的持有时间
     * @return 是否成功获取锁
     */
    boolean acquire(String lockKey, Duration waitTime, Duration leaseTime);

    /**
     * 释放锁
     *
     * @param lockKey 锁的唯一标识
     */
    void release(String lockKey);

    /**
     * 检查锁是否被持有
     *
     * @param lockKey 锁的唯一标识
     * @return 锁是否被持有
     */
    boolean isLocked(String lockKey);

    /**
     * 检查当前线程是否持有锁
     *
     * @param lockKey 锁的唯一标识
     * @return 当前线程是否持有锁
     */
    boolean isHeldByCurrentThread(String lockKey);
}
