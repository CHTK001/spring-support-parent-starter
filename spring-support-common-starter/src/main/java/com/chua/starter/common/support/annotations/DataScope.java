package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.constant.DataFilterTypeEnum;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * <p>用于声明式控制方法级别的数据权限</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 使用用户当前的数据权限
 * @DataScope
 * List<SysUser> selectUserList();
 *
 * // 强制使用指定的数据权限类型
 * @DataScope(value = DataFilterTypeEnum.DEPT)
 * List<SysUser> selectDeptUserList();
 *
 * // 禁用数据权限
 * @DataScope(enabled = false)
 * List<SysUser> selectAllUserList();
 * }</pre>
 *
 * @author CH
 * @since 2022/7/29 8:23
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 数据权限类型
     * <p>默认使用用户当前的数据权限类型（由登录时从部门配置获取）</p>
     * <p>如果指定了具体类型，则强制使用指定的类型</p>
     *
     * @return 数据权限类型
     */
    DataFilterTypeEnum value() default DataFilterTypeEnum.ALL;

    /**
     * 是否启用数据权限
     * <p>默认启用，设置为 false 可以禁用该方法的数据权限</p>
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 是否使用用户当前的数据权限
     * <p>当设置为 true 时，忽略 value 属性，使用用户登录时的数据权限</p>
     *
     * @return 是否使用用户当前权限
     */
    boolean useUserPermission() default true;

    /**
     * 部门字段别名
     * <p>用于指定 SQL 中部门ID字段的别名，默认使用全局配置</p>
     *
     * @return 部门字段别名
     */
    String deptAlias() default "";

    /**
     * 用户字段别名
     * <p>用于指定 SQL 中用户ID字段的别名，默认使用全局配置</p>
     *
     * @return 用户字段别名
     */
    String userAlias() default "";
}

