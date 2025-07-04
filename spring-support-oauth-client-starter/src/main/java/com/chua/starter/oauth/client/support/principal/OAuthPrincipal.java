package com.chua.starter.oauth.client.support.principal;

import com.chua.starter.oauth.client.support.user.UserResume;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * OAuth Principal实现
 * 
 * 实现java.security.Principal接口，集成OAuth用户信息，
 * 提供用户身份验证和权限管理功能。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Getter
@RequiredArgsConstructor
public class OAuthPrincipal implements Principal {

    /**
     * 用户信息
     */
    private final UserResume userResume;

    /**
     * 认证类型
     */
    private final String authType;

    /**
     * 是否已认证
     */
    private final boolean authenticated;

    /**
     * 创建OAuth Principal实例
     * 
     * @param userResume 用户信息
     * @param authType 认证类型
     * @param authenticated 是否已认证
     * @return OAuthPrincipal实例
     */
    public static OAuthPrincipal create(UserResume userResume, String authType, boolean authenticated) {
        return new OAuthPrincipal(userResume, authType, authenticated);
    }

    /**
     * 创建已认证的OAuth Principal实例
     * 
     * @param userResume 用户信息
     * @param authType 认证类型
     * @return 已认证的OAuthPrincipal实例
     */
    public static OAuthPrincipal authenticated(UserResume userResume, String authType) {
        return new OAuthPrincipal(userResume, authType, true);
    }

    /**
     * 创建未认证的OAuth Principal实例
     * 
     * @return 未认证的OAuthPrincipal实例
     */
    public static OAuthPrincipal unauthenticated() {
        return new OAuthPrincipal(null, null, false);
    }

    @Override
    public String getName() {
        return userResume != null ? userResume.getUsername() : "anonymous";
    }

    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public String getUserId() {
        return userResume != null ? userResume.getUserId() : null;
    }

    /**
     * 获取用户真实姓名
     * 
     * @return 用户真实姓名
     */
    public String getRealName() {
        return userResume != null ? userResume.getName() : null;
    }

    /**
     * 获取租户ID
     * 
     * @return 租户ID
     */
    public String getTenantId() {
        return userResume != null ? userResume.getTenantId() : null;
    }

    /**
     * 获取部门ID
     * 
     * @return 部门ID
     */
    public String getDeptId() {
        return userResume != null ? userResume.getDeptId() : null;
    }

    /**
     * 获取用户角色
     * 
     * @return 用户角色集合
     */
    public Set<String> getRoles() {
        return userResume != null && userResume.getRoles() != null ? 
               userResume.getRoles() : Collections.emptySet();
    }

    /**
     * 获取用户权限
     * 
     * @return 用户权限集合
     */
    public Set<String> getPermissions() {
        return userResume != null && userResume.getPermission() != null ? 
               userResume.getPermission() : Collections.emptySet();
    }

    /**
     * 获取扩展属性
     * 
     * @return 扩展属性Map
     */
    public Map<String, Object> getExtensions() {
        return userResume != null && userResume.getExt() != null ? 
               userResume.getExt() : Collections.emptyMap();
    }

    /**
     * 检查用户是否具有指定角色
     * 
     * @param role 角色名称
     * @return 是否具有该角色
     */
    public boolean hasRole(String role) {
        return userResume != null && userResume.hasRole(role);
    }

    /**
     * 检查用户是否具有指定角色中的任意一个
     * 
     * @param roles 角色名称数组
     * @return 是否具有任意一个角色
     */
    public boolean hasAnyRole(String... roles) {
        return userResume != null && userResume.hasRole(roles);
    }

    /**
     * 检查用户是否具有指定权限
     * 
     * @param permission 权限名称
     * @return 是否具有该权限
     */
    public boolean hasPermission(String permission) {
        return userResume != null && userResume.hasPermission(permission);
    }

    /**
     * 检查用户是否具有指定权限中的任意一个
     * 
     * @param permissions 权限名称数组
     * @return 是否具有任意一个权限
     */
    public boolean hasAnyPermission(String... permissions) {
        return userResume != null && userResume.hasPermission(permissions);
    }

    /**
     * 检查用户是否为管理员
     * 
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return userResume != null && userResume.isAdmin();
    }

    /**
     * 获取用户手机号
     * 
     * @return 手机号
     */
    public String getPhone() {
        return userResume != null ? userResume.getPhone() : null;
    }

    /**
     * 获取用户身份证号
     * 
     * @return 身份证号
     */
    public String getCard() {
        return userResume != null ? userResume.getCard() : null;
    }

    /**
     * 获取用户性别
     * 
     * @return 性别
     */
    public String getSex() {
        return userResume != null ? userResume.getSex() : null;
    }

    /**
     * 获取最后登录IP
     * 
     * @return 最后登录IP
     */
    public String getLastIp() {
        return userResume != null ? userResume.getLastIp() : null;
    }

    @Override
    public String toString() {
        return "OAuthPrincipal{" +
                "name='" + getName() + '\'' +
                ", userId='" + getUserId() + '\'' +
                ", authType='" + authType + '\'' +
                ", authenticated=" + authenticated +
                ", roles=" + getRoles().size() +
                ", permissions=" + getPermissions().size() +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        OAuthPrincipal that = (OAuthPrincipal) obj;
        
        if (authenticated != that.authenticated) return false;
        if (userResume != null ? !userResume.equals(that.userResume) : that.userResume != null) return false;
        return authType != null ? authType.equals(that.authType) : that.authType == null;
    }

    @Override
    public int hashCode() {
        int result = userResume != null ? userResume.hashCode() : 0;
        result = 31 * result + (authType != null ? authType.hashCode() : 0);
        result = 31 * result + (authenticated ? 1 : 0);
        return result;
    }
}
