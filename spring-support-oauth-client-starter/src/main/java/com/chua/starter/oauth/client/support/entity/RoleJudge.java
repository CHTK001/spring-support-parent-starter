package com.chua.starter.oauth.client.support.entity;

import java.util.Set;

/**
 * 角色判断器
 * @since 2024/12/31
 * @author CH
 */
public class RoleJudge {
    /** Roles */
    private final Set<String> roles;

    /**
     * 创建 RoleJudge 实例
     * @param roles roles
     */
    public RoleJudge(Set<String> roles) {
        this.roles = roles;
    }


    /**
     * 是否拥有角色
     * @param role 角色
     * @return 是否拥有角色
     */
    public boolean hasRoleCaseInsensitive(String role) {
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }
    /**
     * 是否拥有角色
     * @param role 角色
     * @return 是否拥有角色
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }


    /**
     * 是否拥有任意角色
     * @param role 角色
     * @return 是否拥有角色
     */
    public boolean hasAnyRoles(String... role) {
        for (String s : role) {
            if (hasRole(s)) {
                return true;
            }
        }
        return false;
    }


}
