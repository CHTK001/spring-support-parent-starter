package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.properties.ControlProperties;

import java.lang.annotation.*;

/**
 *平台名称
 *
 * @author Administrator
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiPlatform {
    /**
     * 平台名称
     */
    String[] value() default {};

}
