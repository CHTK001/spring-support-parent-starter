package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * API Version type
 *
 * @author Administrator
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {
    /**
     * api version begin 1
     */
    double version() default 1;
}
