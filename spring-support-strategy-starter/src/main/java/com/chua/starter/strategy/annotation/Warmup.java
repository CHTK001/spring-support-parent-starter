package com.chua.starter.strategy.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 服务预热注解
 * <p>
 * 用于标记需要在应用启动时预热的方法或Bean。
 * 预热可以避免冷启动带来的性能问题，提前初始化资源、加载缓存等。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌─────────────────────────────────────────┐
 * │           应用启动完成                     │
 * │       (ApplicationReadyEvent)             │
 * └───────────────────┬─────────────────────┘
 *                     ▼
 * ┌─────────────────────────────────────────┐
 * │      收集所有@Warmup注解的方法           │
 * └───────────────────┬─────────────────────┘
 *                     ▼
 * ┌─────────────────────────────────────────┐
 * │       按优先级(order)排序任务             │
 * └───────────────────┬─────────────────────┘
 *                     ▼
 * ┌─────────────────────────────────────────┐
 * │   分离同步任务和异步任务(async)           │
 * └──────────┬──────────┬────────────────────┘
 *            ▼          ▼
 * ┌────────────┐  ┌────────────────────────┐
 * │  同步执行  │  │   异步执行（虚拟线程）    │
 * │ (顺序执行) │  │  (并行执行不阻塞启动)   │
 * └────────────┘  └────────────────────────┘
 *            │                    │
 *            ▼                    ▼
 * ┌─────────────────────────────────────────┐
 * │  对每个任务：                            │
 * │  1. 准备参数(参数提供者或默认值)           │
 * │  2. 执行iteration次方法调用               │
 * │  3. 超时控制(timeout)                     │
 * │  4. 失败处理(failOnError)                 │
 * └───────────────────┬─────────────────────┘
 *                     ▼
 * ┌─────────────────────────────────────────┐
 * │       输出预热统计信息                     │
 * │   (总任务数、成功数、失败数、耗时)            │
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Warmup {

    /**
     * 预热任务名称
     * <p>
     * 默认使用方法名或类名
     * </p>
     *
     * @return 预热任务名称
     */
    String name() default "";

    /**
     * 预热优先级
     * <p>
     * 数值越小优先级越高，默认为0
     * </p>
     *
     * @return 优先级
     */
    int order() default 0;

    /**
     * 是否异步执行预热
     * <p>
     * 异步预热不会阻塞应用启动
     * </p>
     *
     * @return 是否异步
     */
    boolean async() default false;

    /**
     * 预热超时时间（毫秒）
     * <p>
     * 超过此时间则放弃预热，0表示不限制
     * </p>
     *
     * @return 超时时间
     */
    long timeout() default 30000L;

    /**
     * 预热失败是否阻止应用启动
     * <p>
     * 如果为true，预热失败会导致应用启动失败
     * </p>
     *
     * @return 是否阻止启动
     */
    boolean failOnError() default false;

    /**
     * 预热执行次数
     * <p>
     * 用于JIT编译优化，多次执行可以触发热点代码优化
     * </p>
     *
     * @return 执行次数
     */
    int iterations() default 1;

    /**
     * 预热时使用的参数提供者
     * <p>
     * 指定一个实现了WarmupParameterProvider接口的Bean名称
     * </p>
     *
     * @return 参数提供者Bean名称
     */
    String parameterProvider() default "";

    /**
     * 预热描述信息
     *
     * @return 描述
     */
    String description() default "";
}
