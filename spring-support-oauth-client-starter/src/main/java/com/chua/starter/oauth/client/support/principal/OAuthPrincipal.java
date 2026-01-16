package com.chua.starter.oauth.client.support.principal;

import com.chua.starter.oauth.client.support.user.UserResume;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.Arrays;
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
@Slf4j
@Getter
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
     * 构造函数
     *
     * @param userResume    用户信息
     * @param authType      认证类型
     * @param authenticated 是否已认证
     */
    public OAuthPrincipal(UserResume userResume, String authType, boolean authenticated) {
        this.userResume = userResume;
        this.authType = authType;
        this.authenticated = authenticated;
        
        log.debug("[Principal构造]创建OAuthPrincipal实例 - 用户: {}, 认证类型: {}, 已认证: {}", 
                 userResume != null ? userResume.getUsername() : "null", 
                 authType, 
                 authenticated);
    }

    /**
     * 创建OAuth Principal实例
     * 
     * @param userResume 用户信息
     * @param authType 认证类型
     * @param authenticated 是否已认证
     * @return OAuthPrincipal实例
     */
    public static OAuthPrincipal create(UserResume userResume, String authType, boolean authenticated) {
        log.info("[Principal创建]创建OAuth Principal - 用户: {}, 认证类型: {}, 认证状态: {}", 
                userResume != null ? userResume.getUsername() : "anonymous",
                authType,
                authenticated ? "已认证" : "未认证");
        
        log.debug("[Principal创建]用户详细信息 - 用户ID: {}, 真实姓名: {}, 租户ID: {}, 部门ID: {}", 
                 userResume != null ? userResume.getUserId() : "null",
                 userResume != null ? userResume.getNickName() : "null",
                 userResume != null ? userResume.getTenantId() : "null",
                 userResume != null ? userResume.getDeptId() : "null");
        
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
        log.info("[Principal认证]创建已认证的OAuth Principal - 用户: {}, 认证类型: {}", 
                userResume != null ? userResume.getUsername() : "anonymous",
                authType);
        
        if (userResume != null && log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("[Principal认证]用户信息 -");
            
            // 基本信息
            if (hasValue(userResume.getUserId())) {
                sb.append(" 用户ID: ").append(userResume.getUserId()).append(",");
            }
            if (hasValue(userResume.getUsername())) {
                sb.append(" 用户名: ").append(userResume.getUsername()).append(",");
            }
            if (hasValue(userResume.getNickName())) {
                sb.append(" 昵称: ").append(userResume.getNickName()).append(",");
            }
            if (hasValue(userResume.getPhone())) {
                sb.append(" 手机: ").append(userResume.getPhone()).append(",");
            }
            if (userResume.isAdmin()) {
                sb.append(" 管理员: 是,");
            }
            
            // 组织信息
            if (hasValue(userResume.getTenantId())) {
                sb.append(" 租户ID: ").append(userResume.getTenantId()).append(",");
            }
            if (hasValue(userResume.getDeptId())) {
                sb.append(" 部门ID: ").append(userResume.getDeptId()).append(",");
            }
            if (hasValue(userResume.getAddress())) {
                sb.append(" 最后登录IP: ").append(userResume.getAddress()).append(",");
            }
            
            // 权限信息
            if (userResume.getRoles() != null && !userResume.getRoles().isEmpty()) {
                sb.append(" 角色: ").append(userResume.getRoles()).append(",");
            }
            if (userResume.getPermission() != null && !userResume.getPermission().isEmpty()) {
                sb.append(" 权限数: ").append(userResume.getPermission().size()).append(",");
            }
            if (userResume.getExt() != null && !userResume.getExt().isEmpty()) {
                sb.append(" 扩展属性: ").append(userResume.getExt().keySet()).append(",");
            }
            
            // 移除末尾逗号
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.setLength(sb.length() - 1);
            }
            
            log.debug(sb.toString());
        }
        
        return new OAuthPrincipal(userResume, authType, true);
    }
    
    /**
     * 判断字符串是否有值
     */
    private static boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * 创建未认证的OAuth Principal实例
     * 
     * @return 未认证的OAuthPrincipal实例
     */
    public static OAuthPrincipal unauthenticated() {
        log.info("[Principal未认证]创建未认证的OAuth Principal (匿名用户)");
        log.debug("[Principal未认证]用户信息为空, 认证类型为空, 认证状态: 未认证");
        return new OAuthPrincipal(null, null, false);
    }

    @Override
    public String getName() {
        String name = userResume != null ? userResume.getUsername() : "anonymous";
        log.debug("[Principal获取]获取用户名: {}", name);
        return name;
    }

    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public String getUserId() {
        String userId = userResume != null ? userResume.getUserId() : null;
        log.debug("[Principal获取]获取用户ID: {}", userId);
        return userId;
    }

    /**
     * 获取用户真实姓名
     * 
     * @return 用户真实姓名
     */
    public String getRealName() {
        String realName = userResume != null ? userResume.getNickName() : null;
        log.debug("[Principal获取]获取真实姓名: {}", realName);
        return realName;
    }

    /**
     * 获取租户ID
     * 
     * @return 租户ID
     */
    public String getTenantId() {
        String tenantId = userResume != null ? userResume.getTenantId() : null;
        log.debug("[Principal获取]获取租户ID: {}", tenantId);
        return tenantId;
    }

    /**
     * 获取部门ID
     * 
     * @return 部门ID
     */
    public String getDeptId() {
        String deptId = userResume != null ? userResume.getDeptId() : null;
        log.debug("[Principal获取]获取部门ID: {}", deptId);
        return deptId;
    }

    /**
     * 获取用户角色
     * 
     * @return 用户角色集合
     */
    public Set<String> getRoles() {
        Set<String> roles = userResume != null && userResume.getRoles() != null ? 
               userResume.getRoles() : Collections.emptySet();
        log.debug("[Principal获取]获取用户角色 - 用户: {}, 角色数量: {}, 角色列表: {}", 
                 getName(), roles.size(), roles);
        return roles;
    }

    /**
     * 获取用户权限
     * 
     * @return 用户权限集合
     */
    public Set<String> getPermissions() {
        Set<String> permissions = userResume != null && userResume.getPermission() != null ? 
               userResume.getPermission() : Collections.emptySet();
        log.debug("[Principal获取]获取用户权限 - 用户: {}, 权限数量: {}", getName(), permissions.size());
        if (log.isTraceEnabled() && !permissions.isEmpty()) {
            log.trace("[Principal获取]用户权限详情: {}", permissions);
        }
        return permissions;
    }

    /**
     * 获取扩展属性
     * 
     * @return 扩展属性Map
     */
    public Map<String, Object> getExtensions() {
        Map<String, Object> extensions = userResume != null && userResume.getExt() != null ? 
               userResume.getExt() : Collections.emptyMap();
        log.debug("[Principal获取]获取扩展属性 - 用户: {}, 属性数量: {}, 属性键: {}", 
                 getName(), extensions.size(), extensions.keySet());
        return extensions;
    }

    /**
     * 检查用户是否具有指定角色
     * 
     * @param role 角色名称
     * @return 是否具有该角色
     */
    public boolean hasRole(String role) {
        boolean result = userResume != null && userResume.hasRole(role);
        
        if (result) {
            log.info("[权限验证通过]用户 [{}] 具有角色 [{}]", getName(), role);
        } else {
            log.info("[权限验证失败]用户 [{}] 不具有角色 [{}]", getName(), role);
        }
        
        log.debug("[权限验证详情]hasRole检查 - 用户: {}, 检查角色: {}, 用户拥有角色: {}, 验证结果: {}", 
                 getName(), role, getRoles(), result ? "通过" : "拒绝");
        
        return result;
    }

    /**
     * 检查用户是否具有指定角色中的任意一个
     * 
     * @param roles 角色名称数组
     * @return 是否具有任意一个角色
     */
    public boolean hasAnyRole(String... roles) {
        boolean result = userResume != null && userResume.hasRole(roles);
        String rolesStr = Arrays.toString(roles);
        
        if (result) {
            log.info("[权限验证通过]用户 [{}] 具有角色列表 {} 中的至少一个", getName(), rolesStr);
        } else {
            log.info("[权限验证失败]用户 [{}] 不具有角色列表 {} 中的任何一个", getName(), rolesStr);
        }
        
        log.debug("[权限验证详情]hasAnyRole检查 - 用户: {}, 检查角色列表: {}, 用户拥有角色: {}, 验证结果: {}", 
                 getName(), rolesStr, getRoles(), result ? "通过" : "拒绝");
        
        return result;
    }

    /**
     * 检查用户是否具有指定权限
     * 
     * @param permission 权限名称
     * @return 是否具有该权限
     */
    public boolean hasPermission(String permission) {
        boolean result = userResume != null && userResume.hasPermission(permission);
        
        if (result) {
            log.info("[权限验证通过]用户 [{}] 具有权限 [{}]", getName(), permission);
        } else {
            log.info("[权限验证失败]用户 [{}] 不具有权限 [{}]", getName(), permission);
        }
        
        log.debug("[权限验证详情]hasPermission检查 - 用户: {}, 用户ID: {}, 检查权限: {}, 验证结果: {}", 
                 getName(), getUserId(), permission, result ? "通过" : "拒绝");
        
        return result;
    }

    /**
     * 检查用户是否具有指定权限中的任意一个
     * 
     * @param permissions 权限名称数组
     * @return 是否具有任意一个权限
     */
    public boolean hasAnyPermission(String... permissions) {
        boolean result = userResume != null && userResume.hasPermission(permissions);
        String permissionsStr = Arrays.toString(permissions);
        
        if (result) {
            log.info("[权限验证通过]用户 [{}] 具有权限列表 {} 中的至少一个", getName(), permissionsStr);
        } else {
            log.info("[权限验证失败]用户 [{}] 不具有权限列表 {} 中的任何一个", getName(), permissionsStr);
        }
        
        log.debug("[权限验证详情]hasAnyPermission检查 - 用户: {}, 用户ID: {}, 检查权限列表: {}, 验证结果: {}", 
                 getName(), getUserId(), permissionsStr, result ? "通过" : "拒绝");
        
        return result;
    }

    /**
     * 检查用户是否为管理员
     * 
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        boolean result = userResume != null && userResume.isAdmin();
        
        log.debug("[管理员检查]用户: {}, 用户ID: {}, 是否管理员: {}", getName(), getUserId(), result ? "是" : "否");
        
        if (result) {
            log.info("[管理员验证]用户 [{}] 是管理员", getName());
        }
        
        return result;
    }

    /**
     * 获取用户手机号
     * 
     * @return 手机号
     */
    public String getPhone() {
        String phone = userResume != null ? userResume.getPhone() : null;
        log.debug("[Principal获取]获取手机号: {}", phone != null ? maskPhone(phone) : "null");
        return phone;
    }

    /**
     * 获取用户身份证号
     * 
     * @return 身份证号
     */
    public String getCard() {
        String card = userResume != null ? null : null;
        log.debug("[Principal获取]获取身份证号: {}", card != null ? maskCard(card) : "null");
        return card;
    }

    /**
     * 获取用户性别
     * 
     * @return 性别
     */
    public String getSex() {
        String sex = userResume != null ? userResume.getSex() : null;
        log.debug("[Principal获取]获取性别: {}", sex);
        return sex;
    }

    /**
     * 获取最后登录IP
     * 
     * @return 最后登录IP
     */
    public String getLastIp() {
        String lastIp = userResume != null ? userResume.getAddress() : null;
        log.debug("[Principal获取]获取最后登录IP: {}", lastIp);
        return lastIp;
    }
    
    /**
     * 脱敏手机号
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
    
    /**
     * 脱敏身份证号
     */
    private String maskCard(String card) {
        if (card == null || card.length() < 10) {
            return card;
        }
        return card.substring(0, 6) + "********" + card.substring(card.length() - 4);
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
