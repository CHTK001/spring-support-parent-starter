package com.chua.starter.mybatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自动填充注解，用于标记需要自动填充的实体类
 *
 * @author CH
 * @since 2025/1/2
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
public @interface AutoFill {

    /**
     * 是否启用自动填充功能
     *
     * @return true-启用，false-禁用
     * @example @AutoFill(value = true)
     */
    boolean value() default true;

    /**
     * 是否启用更新时自动填充
     *
     * @return true-启用，false-禁用
     * @example @AutoFill(update = false) // 更新时不进行自动填充
     */
    boolean update() default true;

    /**
     * 是否启用插入时自动填充
     *
     * @return true-启用，false-禁用
     * @example @AutoFill(insert = false) // 插入时不进行自动填充
     */
    boolean insert() default true;
}
