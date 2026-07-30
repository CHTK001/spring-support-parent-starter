package com.chua.starter.job.support;

import java.lang.annotation.*;

/**
 * 自定义定时任务注解
 * 标记方法为定时任务
 *
 * @author CH
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scheduled {
    /**
     * 任务名称（可选）
     */
    String name() default "";

    /**
     * Cron 表达式（优先级最高）
     */
    String value() default "";

    /**
     * 调度策略
     * 支持格式：
     * - fixedRate=5000（固定频率，毫秒）
     * - fixedDelay=5000（固定延迟，毫秒）
     * - once=1000（执行一次，延迟毫秒）
     */
    String strategy() default "";
}
