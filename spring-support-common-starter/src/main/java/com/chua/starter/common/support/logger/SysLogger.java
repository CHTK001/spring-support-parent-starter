package com.chua.starter.common.support.logger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日志
 *
 * @author CH
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SysLogger {

    /**
     * 模块名称
     *
     * @return 模块名称
     */
    String value() default "";

    /**
     * 动作
     *
     * @return 动作
     */
    String action() default "";

    /**
     * 模块编号
     *
     * @return 模块编号
     */
    String code() default "";

    /**
     * 内容
     *
     * @return 内容
     */
    String content() default "";
}
