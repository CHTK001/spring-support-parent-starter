package com.chua.starter.common.support.api.permission;

import com.chua.starter.common.support.annotations.Permission;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link Permission} 的判定规则。
 *
 * <p>注解两组属性分别比对：{@code role()} 只与用户角色比对；{@code value()/permission()}
 * 与「用户角色 ∪ 权限标识」比对，因为历史上既写过 {@code @Permission("admin")}（角色名），
 * 也写过 {@code @Permission("sys:soft:update")}（权限标识）。超级管理员放行全部受限出口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class PermissionRules {

    /**
     * 超级管理员角色，放行所有受限出口
     */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * 普通管理员角色，放行除超级管理员专属之外的所有受限出口；
     * 与登录鉴权侧 {@code PermissionInterceptor} 的管理员判定保持一致，
     * 避免两套权限机制对同一角色结论不一致。
     */
    public static final String ADMIN = "ADMIN";

    private PermissionRules() {
    }

    /**
     * 判定一次 {@link Permission} 校验。
     *
     * @param permission  注解实例，不可为空
     * @param roles       当前用户角色
     * @param permissions 当前用户权限标识
     * @return 放行返回 {@code null}；拒绝返回给前端的提示文案
     */
    public static String evaluate(Permission permission, Collection<String> roles, Collection<String> permissions) {
        Set<String> required = new LinkedHashSet<>();
        Collections.addAll(required, permission.role());
        Collections.addAll(required, permission.value());
        Collections.addAll(required, permission.permission());
        required.removeIf(s -> s == null || s.isBlank());
        if (required.isEmpty()) {
            return null;
        }
        if (containsIgnoreCase(roles, SUPER_ADMIN)) {
            return null;
        }
        // 普通管理员放行：与登录鉴权侧拦截器保持一致；
        // 仅当接口显式声明为超级管理员专属（role 恰好为单个 SUPER_ADMIN）时才拒绝
        if (containsIgnoreCase(roles, ADMIN)) {
            if (isSuperAdminOnly(permission)) {
                return "权限不足，需要：" + SUPER_ADMIN;
            }
            return null;
        }
        Set<String> granted = new LinkedHashSet<>(roles);
        granted.addAll(permissions);
        for (String need : required) {
            if (containsIgnoreCase(granted, need)) {
                return null;
            }
        }
        return "权限不足，需要：" + String.join("/", required);
    }

    /**
     * 判断接口是否为超级管理员专属：{@code role()} 恰好声明单个 SUPER_ADMIN。
     *
     * @param permission 注解实例
     * @return 超级管理员专属返回 {@code true}
     */
    private static boolean isSuperAdminOnly(Permission permission) {
        String[] role = permission.role();
        return role != null && role.length == 1
                && role[0] != null && role[0].trim().equalsIgnoreCase(SUPER_ADMIN);
    }

    /**
     * 忽略大小写判断集合是否含某个元素
     *
     * @param values 集合
     * @param value  元素
     * @return 是否命中的结果
     */
    private static boolean containsIgnoreCase(Collection<String> values, String value) {
        for (String item : values) {
            if (item != null && item.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
