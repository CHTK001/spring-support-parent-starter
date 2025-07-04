package com.chua.starter.common.support.oauth;

import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 当前用户
 *
 * @author CH
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private static final String ANY = "*";
    /**
     * unionId
     */
    private String unionId;
    /**
     * openId
     */
    private String openId;
    /**
     * 索引
     */
    private String uid;
    /**
     * 用户id
     */
    private String userId;
    /**
     * 租户id
     */
    private String tenantId;
    /**
     * 名称
     */
    private String username;
    /**
     * 机构id
     */
    private String deptId;
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
     * 权限
     */
    private Set<String> permission;

    /**
     * 数据权限(部门)
     */
    private DataFilterTypeEnum dataPermission;
    /**
     * 数据权限规则
     */
    private String dataPermissionRule;
    /**
     * d
     * /**
     * 角色
     */
    private Set<String> roles;

    private Map<String, Object> ext;
    /**
     * 最后一次登录地址
     */
    private String lastIp;

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
        if (roles.length == 0) {
            return true;
        }

        if (CollectionUtils.isEmpty(this.roles)) {
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
        if (value.length == 0) {
            return true;
        }

        if (CollectionUtils.isEmpty(this.permission)) {
            return false;
        }
        for (String s : value) {
            if (this.permission.contains(s)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 是否管理员
     *
     * @return 是否管理员
     */
    public boolean isAdmin() {
        return null != this.roles && this.roles.contains("ADMIN");
    }

    /**
     * 是否部门权限
     *
     * @return 是否部门权限
     */
    public boolean isDept() {
        return null != this.roles && this.roles.contains("DEPT");
    }
}
