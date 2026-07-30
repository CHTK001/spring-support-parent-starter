package com.chua.starter.strategy.distributed;

/**
 * 策略防抖器接口
 * <p>
 * 提供统一的防抖能力，支持本地和分布式实现。
 * 可通过配置切换不同实现：local（本地）、redis（分布式）等。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌───────────────────────────────────────────────────────┐
 * │                   请求到达                           │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │         tryAcquire(key, durationMillis)               │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *   ┌───────────────────────────────────────────────────┐
 *   │  本地实现(LocalDebounce)：                            │
 *   │  - 使用ConcurrentHashMap + 过期时间戳               │
 *   │  - 定时清理过期键                                   │
 *   ├───────────────────────────────────────────────────┤
 *   │  分布式实现(RedisDebounce)：                          │
 *   │  - 使用Redis SETNX + EXPIRE保证原子性                │
 *   │  - 支持分布式环境的防抖控制                          │
 *   └─────────────────────────┬─────────────────────────┘
 *                           ▼
 *              ┌─────────────────────────┐
 *              │ key在防抖期内已存在？        │
 *              └───────────┬─────────────┘
 *                    ┌─────┴─────┐
 *               Yes  │           │  No
 *            (被防抖)  │           │ (首次请求)
 *                    ▼           ▼
 *          ┌─────────────┐  ┌────────────────────────┐
 *          │ return false │  │  设置key，过期时间=duration │
 *          │  (被防抖)     │  └───────────┬────────────┘
 *          └─────────────┘              ▼
 *                              ┌──────────────┐
 *                              │  return true  │
 *                              │(允许执行操作)   │
 *                              └──────────────┘
 *
 * 防抖时序图：
 *   时间线：  |----duration----|----duration----|----duration----|
 *   请求：     R1   R2  R3       R4        R5  R6          R7
 *   结果：     ✓    ✗   ✗        ✓         ✗   ✗           ✓
 *           (通过)(拒)(拒)     (通过)    (拒)(拒)       (通过)
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public interface StrategyDebounce {

    /**
     * 尝试获取防抖锁
     *
     * @param key            防抖键
     * @param durationMillis 防抖时间间隔（毫秒）
     * @return true-获取成功（未被防抖），false-获取失败（被防抖）
     */
    boolean tryAcquire(String key, long durationMillis);

    /**
     * 尝试获取防抖锁（使用字符串格式的时间间隔）
     *
     * @param key      防抖键
     * @param duration 防抖时间间隔，支持格式：1000, 1S, 1MIN, 1H
     * @return true-获取成功，false-被防抖
     */
    boolean tryAcquire(String key, String duration);

    /**
     * 释放防抖锁
     *
     * @param key 防抖键
     */
    void release(String key);

    /**
     * 检查是否被防抖
     *
     * @param key 防抖键
     * @return true-被防抖中，false-未被防抖
     */
    boolean isDebounced(String key);

    /**
     * 获取防抖器类型
     *
     * @return 防抖器类型
     */
    String getType();
}
