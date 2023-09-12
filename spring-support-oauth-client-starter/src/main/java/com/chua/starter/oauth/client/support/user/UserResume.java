package com.chua.starter.oauth.client.support.user;

import com.google.common.base.Strings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户信息
 *
 * @author CH
 * @since 2022/7/23 8:48
 */
@Data
public class UserResume  {
    /**
     * 索引
     */
    private String uid;
    /**
     * 名称
     */
    private String username;
    /**
     * 电话号码
     */
    private String phone;
    /**
     * 身份证号
     */
    private String card;
    /**
     * 姓名
     */
    private String name;
    /**
     * 性别
     */
    private String sex;
    /**
     * d
     * /**
     * 角色
     */
    private Set<String> roles;
    /**
     * 权限
     */
    private Set<String> permission;
    /**
     * 最后一次登录地址
     */
    private String lastIp;

    private Map<String, Object> ext;

    private static final String ANY = "*";

    /**
     * 是否具备某个权限
     *
     * @param permission 权限
     * @return 是否具备某个权限
     */
    public boolean hasPermission(String permission) {
        if (Strings.isNullOrEmpty(permission)) {
            return false;
        }

        if (permission.contains(ANY)) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            for (String s : this.permission) {
                if (antPathMatcher.match(permission, s)) {
                    return true;
                }
            }
        }

        for (String s : this.permission) {
            if (Objects.equals(permission, s)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 是否具备某个角色
     *
     * @param roles 角色
     * @return 是否具备某个角色
     */
    public boolean hasRole(String[] roles) {
        if(roles.length == 0) {
            return true;
        }

        if(CollectionUtils.isEmpty(this.roles)) {
            return false;
        }

        boolean hasRole = false;
        for (String role : roles) {
            hasRole |= hasRole(role);
        }

        return hasRole;
    }

    /**
     * 是否具备某个角色
     *
     * @param role 角色
     * @return 是否具备某个角色
     */
    public boolean hasRole(String role) {
        if (Strings.isNullOrEmpty(role)) {
            return false;
        }

        if (role.contains(ANY)) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            for (String s : roles) {
                if (antPathMatcher.match(role, s)) {
                    return true;
                }
            }
        }

        for (String s : roles) {
            if (Objects.equals(role, s)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPermission(String[] value) {
        if(value.length == 0) {
            return true;
        }

        if(CollectionUtils.isEmpty(this.permission)) {
            return false;
        }
        for (String s : value) {
            if(this.permission.contains(s)) {
                return true;
            }
        }

        return false;
    }

    public boolean isAdmin() {
        return null != this.roles && this.roles.contains("ADMIN");
    }
}
