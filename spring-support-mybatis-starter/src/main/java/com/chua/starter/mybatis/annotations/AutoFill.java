package com.chua.starter.mybatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自动填充
 * @author CH
 * @since 2025/1/2
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
public @interface AutoFill {

    /**
     *
     * @return
     */
    boolean value() default true;

    /**
     *  更新填充
     * @return
     */
    boolean update() default true;


    /**
     *  插入填充
     * @return
     */
    boolean insert() default true;
}
