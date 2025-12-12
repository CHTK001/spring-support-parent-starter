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
