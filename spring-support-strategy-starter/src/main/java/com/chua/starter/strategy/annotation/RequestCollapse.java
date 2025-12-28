package com.chua.starter.strategy.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求合并注解
 * <p>
 * 用于将多个相似的请求合并为一个批量请求执行，减少网络开销和后端压力。
 * 适用于高并发场景下的批量查询优化。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌───────────────────────────────────────────────────────┐
 * │              多个并发请求同时到达                     │
 * │       Request1   Request2   Request3 ...         │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │           解析keyExpression提取请求Key                │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │            请求加入合并器的等待队列                     │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *   ┌───────────────────────────────────────────────────┐
 *   │  触发条件（满足其一即执行批量请求）：              │
 *   │  1. 队列数量 >= maxBatchSize                     │
 *   │  2. 等待时间 >= windowTime                       │
 *   └─────────────────────────┬─────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │        收集所有Key，调用batchMethod批量执行            │
 * │             (使用虚拟线程异步执行)                   │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │        批量方法返回Map<Key, Result>                  │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │            将结果分发给各个等待的请求                 │
 * │     Request1 → Result1,  Request2 → Result2 ...  │
 * └───────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestCollapse {

    /**
     * 合并器名称
     * <p>
     * 用于标识不同的合并器实例
     * </p>
     *
     * @return 合并器名称
     */
    String name();

    /**
     * 批量处理方法名
     * <p>
     * 指定处理批量请求的方法名，该方法必须：
     * - 在同一个类中
     * - 参数为List类型（包含所有单个请求的参数）
     * - 返回值为Map类型（key为请求参数，value为结果）
     * </p>
     *
     * @return 批量处理方法名
     */
    String batchMethod();

    /**
     * 合并窗口时间（毫秒）
     * <p>
     * 在此时间窗口内的请求会被合并
     * </p>
     *
     * @return 窗口时间
     */
    long windowTime() default 10L;

    /**
     * 最大批量大小
     * <p>
     * 达到此数量后立即执行批量请求，不等待窗口时间
     * </p>
     *
     * @return 最大批量大小
     */
    int maxBatchSize() default 100;

    /**
     * 请求超时时间（毫秒）
     * <p>
     * 单个请求等待批量结果的最大时间
     * </p>
     *
     * @return 超时时间
     */
    long timeout() default 5000L;

    /**
     * 是否启用请求合并
     * <p>
     * 可用于运行时动态开关
     * </p>
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 参数key的SpEL表达式
     * <p>
     * 用于从方法参数中提取合并key，默认使用第一个参数
     * </p>
     *
     * @return SpEL表达式
     */
    String keyExpression() default "#p0";
}
