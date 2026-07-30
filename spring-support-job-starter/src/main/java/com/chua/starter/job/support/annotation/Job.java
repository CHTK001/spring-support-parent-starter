package com.chua.starter.job.support.annotation;

import java.lang.annotation.*;

/**
 * 定时任务注解
 * <p>
 * 标注在方法上，用于注册定时任务处理器
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Job {

    /**
     * 任务处理器名称
     *
     * @return 名称
     */
    String value();

    /**
     * 调度类型。
     * <p>
     * 可选值：cron / fixed / fixed_ms / delay / at / none
     * </p>
     */
    String scheduleType() default "";

    /**
     * 调度配置。
     * <p>
     * scheduleType=cron 时为 cron 表达式；
     * scheduleType=fixed/fixed_ms 时为间隔时间。
     * scheduleType=delay 时为延迟毫秒数；
     * scheduleType=at 时为绝对时间字符串或时间戳。
     * </p>
     */
    String scheduleTime() default "";

    /**
     * 任务描述。
     */
    String desc() default "";

    /**
     * 负责人。
     */
    String author() default "";

    /**
     * 告警邮箱。
     */
    String alarmEmail() default "";

    /**
     * 是否自动启动。
     */
    boolean autoStart() default false;

    /**
     * 失败重试次数。
     * <p>
     * 小于 0 表示沿用已有值或框架默认值。
     * </p>
     */
    int failRetry() default -1;

    /**
     * 执行超时时间（秒）。
     * <p>
     * 小于 0 表示沿用已有值或框架默认值。
     * </p>
     */
    int executeTimeout() default -1;

    /**
     * 重试间隔（秒）。
     * <p>
     * 小于 0 表示沿用已有值或框架默认值。
     * </p>
     */
    int retryInterval() default -1;

    /**
     * 错失策略。
     */
    String misfireStrategy() default "";

    /**
     * 分发模式。
     * <p>
     * 典型值：LOCAL / REMOTE。
     * </p>
     */
    String dispatchMode() default "";

    /**
     * 指定远程执行器地址。
     * <p>
     * 当分发模式为 REMOTE 时可指定任务级别的目标执行器。
     * </p>
     */
    String remoteExecutorAddress() default "";

    /**
     * 异常回调处理器名称。
     */
    String exceptionCallback() default "";

    /**
     * 重试前回调处理器名称。
     */
    String retryCallback() default "";

    /**
     * 初始化方法名称
     *
     * @return 方法名
     */
    String init() default "";

    /**
     * 销毁方法名称
     *
     * @return 方法名
     */
    String destroy() default "";
}
