package com.chua.starter.mybatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自动填充注解，用于标记需要自动填充的实体类
 *
 * @since 2025/1/2
 * @author CH
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
public @interface AutoFill {

    /**
     * 是否启用自动填充功能
     *
     * @return true-启用，false-禁用
     * @example @autofill(值 = true)
     */
    boolean value() default true;

    /**
     * 是否启用更新时自动填充
     *
     * @return true-启用，false-禁用
     * @example @autofill(更新 = false) // 更新时不进行自动填充
     */
    boolean update() default true;

    /**
     * 是否启用插入时自动填充
     *
     * @return true-启用，false-禁用
     * @example @autofill(插入 = false) // 插入时不进行自动填充
     */
    boolean insert() default true;
}
