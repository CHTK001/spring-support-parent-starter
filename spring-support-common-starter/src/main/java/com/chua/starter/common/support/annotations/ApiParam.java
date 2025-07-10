package com.chua.starter.common.support.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * api参数
 *
 * @author CH
 * @since 2025/7/8 11:32
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiParam {
    /**
     * 是否允许接收参数（默认false）
     */
    boolean allowReceive() default false;

    /**
     * 是否允许返回（默认false）
     */
    boolean allowReturn() default false;

}
