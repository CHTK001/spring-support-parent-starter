package com.chua.starter.strategy.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性控制注解
 * <p>
 * 用于防止接口重复提交，保证同一请求在指定时间内只能执行一次。
 * 支持多种幂等键生成策略和存储方式。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌─────────────────────────────────────────────────┐
 * │                   方法调用                        │
 * └───────────────────────┬─────────────────────────┘
 *                         ▼
 * ┌─────────────────────────────────────────────────┐
 * │    根据keyStrategy生成幂等键                   │
 * │    - SPEL: SpEL表达式                          │
 * │    - PARAMS_MD5: 参数MD5                       │
 * │    - BODY_MD5: 请求体MD5                       │
 * │    - TOKEN: 请求头Token                        │
 * │    - USER_METHOD: 用户ID+方法名                  │
 * └───────────────────────┬─────────────────────────┘
 *                         ▼
 * ┌─────────────────────────────────────────────────┐
 * │  检查幂等键是否存在（Redis或本地缓存）             │
 * └───────────────────────┬─────────────────────────┘
 *                         ▼
 *                 ┌─────────────┐
 *                 │   键存在？   │
 *                 └──────┬──────┘
 *            否 ┌──────┴──────┐ 是
 *               ▼              ▼
 * ┌──────────────────┐  ┌───────────────────────┐
 * │  设置幂等键并执行  │  │  抛出IdempotentException │
 * │    目标方法       │  │     (重复提交)           │
 * └─────────┬────────┘  └───────────────────────┘
 *           ▼
 *   ┌───────────────┐
 *   │  执行成功？   │
 *   └──────┬────────┘
 *      是 │  否
 *        ▼    ▼
 * ┌──────────────────┐  ┌──────────────────┐
 * │ deleteOnSuccess │  │  删除幂等键      │
 * │  true:删除键     │  │  (允许重试)       │
 * │  false:保留键    │  └──────────────────┘
 * └──────────────────┘
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等键表达式
     * <p>
     * 支持SpEL表达式，可以从方法参数、请求头、Session等获取值。
     * 特殊变量：
     * - #request: HttpServletRequest对象
     * - #session: HttpSession对象
     * - #header('X-Token'): 获取请求头
     * - #param('id'): 获取请求参数
     * </p>
     *
     * @return 幂等键表达式
     */
    String key() default "";

    /**
     * 幂等键前缀
     *
     * @return 前缀
     */
    String prefix() default "strategy:idempotent:";

    /**
     * 幂等有效期
     * <p>
     * 在此时间内相同的请求会被拦截
     * </p>
     *
     * @return 有效期
     */
    long timeout() default 5L;

    /**
     * 时间单位
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 幂等键生成策略
     *
     * @return 生成策略
     */
    KeyStrategy keyStrategy() default KeyStrategy.SPEL;

    /**
     * 重复提交时的错误消息
     *
     * @return 错误消息
     */
    String message() default "请勿重复提交";

    /**
     * 是否在方法执行完成后删除幂等键
     * <p>
     * 如果为true，方法执行成功后立即删除键，允许再次提交。
     * 如果为false，必须等待超时后才能再次提交。
     * </p>
     *
     * @return 是否执行后删除
     */
    boolean deleteOnSuccess() default false;

    /**
     * 幂等键生成策略枚举
     */
    enum KeyStrategy {
        /**
         * 使用SpEL表达式
         */
        SPEL,
        /**
         * 使用请求参数的MD5值
         */
        PARAMS_MD5,
        /**
         * 使用请求体的MD5值
         */
        BODY_MD5,
        /**
         * 使用Token（从请求头或参数获取）
         */
        TOKEN,
        /**
         * 使用用户ID + 方法名
         */
        USER_METHOD
    }
}
