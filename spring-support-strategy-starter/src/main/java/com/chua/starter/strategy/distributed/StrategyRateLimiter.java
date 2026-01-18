package com.chua.starter.strategy.distributed;

/**
 * 策略限流器接口
 * <p>
 * 提供统一的限流能力，支持本地和分布式实现。
 * 可通过配置切换不同实现：local（本地）、redis（分布式）等。
 * </p>
 *
 * <pre>
 * 执行流程（滑动窗口算法）：
 * ┌───────────────────────────────────────────────────────┐
 * │                   请求到达                           │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │      tryAcquire(key, limitForPeriod, periodSeconds)   │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *   ┌───────────────────────────────────────────────────┐
 *   │  本地实现(LocalRateLimiter)：                          │
 *   │  - 使用Resilience4j RateLimiter                     │
 *   │  - 基于内存的令牌桶算法                             │
 *   ├───────────────────────────────────────────────────┤
 *   │  分布式实现(RedisRateLimiter)：                        │
 *   │  - 使用Redis Lua脚本保证原子性                      │
 *   │  - 滑动窗口计数：ZSET存储时间戳                       │
 *   └─────────────────────────┬─────────────────────────┘
 *                           ▼
 *              ┌─────────────────────────────┐
 *              │ 当前窗口请求数 < limitForPeriod? │
 *              └───────────┬─────────────────┘
 *                    ┌─────┴─────┐
 *               Yes  │           │  No
 *                    ▼           ▼
 *          ┌─────────────┐  ┌───────────────────────┐
 *          │  return true │  │ timeoutMillis > 0?       │
 *          │ (获取许可成功)  │  └───────────┬───────────┘
 *          └─────────────┘        ┌─────┴─────┐
 *                          Yes  │           │  No
 *                               ▼           ▼
 *                      ┌───────────┐  ┌─────────────┐
 *                      │等待并重试   │  │ return false │
 *                      │直到超时     │  │  (被限流)     │
 *                      └───────────┘  └─────────────┘
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public interface StrategyRateLimiter {

    /**
     * 尝试获取许可
     *
     * @param key            限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds  周期时间（秒）
     * @return true-获取成功，false-被限流
     */
    boolean tryAcquire(String key, int limitForPeriod, int periodSeconds);

    /**
     * 尝试获取许可（带超时）
     *
     * @param key             限流键
     * @param limitForPeriod  周期内允许的请求数
     * @param periodSeconds   周期时间（秒）
     * @param timeoutMillis   超时时间（毫秒）
     * @return true-获取成功，false-被限流或超时
     */
    boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis);

    /**
     * 获取当前剩余许可数
     *
     * @param key           限流键
     * @param limitForPeriod 周期内允许的请求数
     * @param periodSeconds 周期时间（秒）
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
     * 获取限流器类型
     *
     * @return 限流器类型
     */
    String getType();
}
