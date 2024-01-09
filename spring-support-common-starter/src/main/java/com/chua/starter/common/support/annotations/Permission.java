package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 权限
 *
 * @author CH
 * @since 2022/7/29 8:23
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Permission {
    /**
     * 权限
     *
     * @return 权限
     */
    String[] value() default {};

    /**
     * 角色
     *
     * @return 角色
     */
    String[] role() default {};
}
