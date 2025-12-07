package com.chua.starter.common.support.annotations;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 权限注解
 * <p>用于标记方法需要特定权限才能访问</p>
 *
 * @author CH
 * @since 2022/7/29 8:23
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Permission {
    
    /**
     * 权限标识列表
     * <p>可配置多个权限标识，用户拥有其中任一权限即可访问该方法</p>
     * <p>示例：{@code @Permission({"login:create", "admin"})}</p>
     *
     * @return 权限标识数组
     */
    @AliasFor("permission")
    String[] value() default {};

    /**
     * 权限标识列表(与value属性互为别名)
     * <p>可配置多个权限标识，用户拥有其中任一权限即可访问该方法</p>
     * <p>示例：{@code @Permission(permission = {"login:create", "admin"})}</p>
     *
     * @return 权限标识数组
     */
    @AliasFor("value")
    String[] permission() default {};
    
    /**
     * 角色标识列表
     * <p>可配置多个角色标识，用户拥有其中任一角色即可访问该方法</p>
     * <p>示例：{@code @Permission(role = {"ADMIN", "USER"})}</p>
     *
     * @return 角色标识数组
     */
    String[] role() default {};
}

