package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * API Version 环境配置
 *
 * @author Administrator
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiProfile {
    /**
     * api version begin 1
     */
    String value() default "";

}
