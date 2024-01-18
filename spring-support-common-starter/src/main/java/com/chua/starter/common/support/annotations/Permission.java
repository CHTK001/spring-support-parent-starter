package com.chua.starter.common.support.annotations;

import org.springframework.core.annotation.AliasFor;

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
    @AliasFor("permission")
    String[] value() default {};

    /**
     * @return {@link String[]}
     */
    @AliasFor("value")
    String[] permission() default {};
    /**
     * 角色
     *
     * @return 角色
     */
    String[] role() default {};
}
