package com.chua.starter.strategy.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 * <p>
 * 用于方法级别的分布式锁控制，支持Redis实现。
 * 可以防止分布式环境下的并发问题。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      方法调用                                │
 * └─────────────────────┬───────────────────────────────────────┘
 *                       ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │              解析SpEL表达式生成锁Key                         │
 * │         支持: #userId, #order.id, #p0等                     │
 * └─────────────────────┬───────────────────────────────────────┘
 *                       ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │         尝试获取分布式锁（带超时等待）                        │
 * │  支持多种实现: Redis/ZooKeeper/Redisson等                 │
 * └─────────────────────┬───────────────────────────────────────┘
 *                       ▼
 *               ┌──────────────┐
 *               │  获取成功？   │
 *               └──────┬───────┘
 *          是 ┌────────┴────────┐ 否
 *             ▼                 ▼
 * ┌───────────────────┐  ┌─────────────────────────┐
 * │    执行目标方法    │  │   根据failStrategy处理   │
 * └─────────┬─────────┘  │  - EXCEPTION: 抛异常     │
 *           ▼            │  - FALLBACK: 执行降级    │
 * ┌──────────────────────────┐ │  - RETURN_NULL: 返回null │
 * │   释放锁(仅锁持有者可释放)  │ │  - SILENT: 静默处理      │
 * └─────────┬────────────────┘ └─────────────────────────┘
 *           ▼
 * ┌───────────────────┐
 * │     返回结果       │
 * └───────────────────┘
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的key
     * <p>
     * 支持SpEL表达式，可以从方法参数中获取值。
     * 例如：#userId、#order.id、#p0
     * </p>
     *
     * @return 锁的key
     */
    String key();

    /**
     * 锁的前缀
     * <p>
     * 默认为"strategy:lock:"
     * </p>
     *
     * @return 锁前缀
     */
    String prefix() default "strategy:lock:";

    /**
     * 等待获取锁的超时时间
     * <p>
     * 默认3秒，0表示不等待
     * </p>
     *
     * @return 等待超时时间
     */
    long waitTime() default 3L;

    /**
     * 锁的持有时间
     * <p>
     * 默认30秒，超过此时间自动释放锁。
     * -1表示不自动释放（需要手动释放或方法执行完毕后释放）
     * </p>
     *
     * @return 持有时间
     */
    long leaseTime() default 30L;

    /**
     * 时间单位
     * <p>
     * 默认秒
     * </p>
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的处理方式
     *
     * @return 失败处理方式
     */
    LockFailStrategy failStrategy() default LockFailStrategy.EXCEPTION;

    /**
     * 获取锁失败时的降级方法名
     * <p>
     * 仅当failStrategy为FALLBACK时有效。
     * 降级方法必须与原方法在同一个类中，且参数列表相同。
     * </p>
     *
     * @return 降级方法名
     */
    String fallbackMethod() default "";

    /**
     * 获取锁失败时的异常消息
     * <p>
     * 仅当failStrategy为EXCEPTION时有效
     * </p>
     *
     * @return 异常消息
     */
    String errorMessage() default "获取分布式锁失败，请稍后重试";

    /**
     * 是否为公平锁
     * <p>
     * 公平锁按照请求顺序获取锁
     * </p>
     *
     * @return 是否公平锁
     */
    boolean fair() default false;

    /**
     * 锁失败策略枚举
     */
    enum LockFailStrategy {
        /**
         * 抛出异常
         */
        EXCEPTION,
        /**
         * 执行降级方法
         */
        FALLBACK,
        /**
         * 返回null
         */
        RETURN_NULL,
        /**
         * 静默处理（记录日志但不抛异常）
         */
        SILENT
    }
}
