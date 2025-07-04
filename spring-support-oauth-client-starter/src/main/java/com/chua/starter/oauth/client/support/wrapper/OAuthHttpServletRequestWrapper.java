package com.chua.starter.oauth.client.support.wrapper;

import com.chua.starter.oauth.client.support.principal.OAuthPrincipal;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.security.Principal;

/**
 * OAuth增强的HttpServletRequestWrapper
 * 
 * 实现HttpServletRequest的认证相关方法，包括isUserInRole、getAuthType、logout、login等，
 * 集成OAuth用户信息和Principal支持。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
public class OAuthHttpServletRequestWrapper extends ContentCachingRequestWrapper {

    /**
     * OAuth Principal
     */
    private final OAuthPrincipal principal;

    /**
     * 认证类型
     */
    private final String authType;

    /**
     * 远程用户名
     */
    private final String remoteUser;

    /**
     * 构造函数
     * 
     * @param request 原始请求
     * @param userResume 用户信息
     * @param authType 认证类型
     */
    public OAuthHttpServletRequestWrapper(HttpServletRequest request, UserResume userResume, String authType) {
        super(request);
        this.authType = authType;
        this.remoteUser = userResume != null ? userResume.getUsername() : null;
        this.principal = userResume != null ? 
                        OAuthPrincipal.authenticated(userResume, authType) : 
                        OAuthPrincipal.unauthenticated();
        
        log.debug("创建OAuthHttpServletRequestWrapper - 用户: {}, 认证类型: {}, 已认证: {}", 
                 remoteUser, authType, principal.isAuthenticated());
    }

    /**
     * 创建已认证的请求包装器
     * 
     * @param request 原始请求
     * @param userResume 用户信息
     * @param authType 认证类型
     * @return 请求包装器
     */
    public static OAuthHttpServletRequestWrapper authenticated(HttpServletRequest request, 
                                                              UserResume userResume, 
                                                              String authType) {
        return new OAuthHttpServletRequestWrapper(request, userResume, authType);
    }

    /**
     * 创建未认证的请求包装器
     * 
     * @param request 原始请求
     * @return 请求包装器
     */
    public static OAuthHttpServletRequestWrapper unauthenticated(HttpServletRequest request) {
        return new OAuthHttpServletRequestWrapper(request, null, null);
    }

    @Override
    public String getAuthType() {
        log.debug("获取认证类型: {}", authType);
        return authType;
    }

    @Override
    public String getRemoteUser() {
        log.debug("获取远程用户: {}", remoteUser);
        return remoteUser;
    }

    @Override
    public Principal getUserPrincipal() {
        log.debug("获取用户Principal: {}", principal);
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        boolean hasRole = principal != null && principal.hasRole(role);
        log.debug("检查用户角色 - 角色: {}, 结果: {}, 用户: {}", role, hasRole, remoteUser);
        return hasRole;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        log.info("执行登录操作 - 用户名: {}", username);
        
        // 检查是否已经登录
        if (principal != null && principal.isAuthenticated()) {
            log.warn("用户已经登录，无法重复登录 - 当前用户: {}, 尝试登录用户: {}", remoteUser, username);
            throw new ServletException("用户已经登录，无法重复登录");
        }
        
        // 这里可以集成实际的登录逻辑
        // 由于OAuth客户端通常通过外部认证服务器进行认证，
        // 这个方法主要用于记录和验证
        log.info("OAuth客户端不支持直接用户名密码登录，请使用OAuth认证流程");
        throw new ServletException("OAuth客户端不支持直接用户名密码登录，请使用OAuth认证流程");
    }

    @Override
    public void logout() throws ServletException {
        log.info("执行登出操作 - 用户: {}", remoteUser);
        
        try {
            // 清除Session中的用户信息
            HttpSession session = getSession(false);
            if (session != null) {
                // 清除用户相关的Session属性
                session.removeAttribute("userResume");
                session.removeAttribute("userInfo");
                session.removeAttribute("principal");
                session.removeAttribute("authType");
                
                // 可以选择是否完全销毁Session
                session.invalidate();
                
                log.info("已清除Session中的用户信息 - 用户: {}", remoteUser);
            }
            
            // 这里可以添加其他登出逻辑，比如：
            // 1. 通知OAuth服务器用户登出
            // 2. 清除缓存中的用户信息
            // 3. 记录登出日志
            
        } catch (Exception e) {
            log.error("登出操作失败 - 用户: {}, 错误: {}", remoteUser, e.getMessage(), e);
            throw new ServletException("登出操作失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查用户是否具有任意一个指定角色
     * 
     * @param roles 角色数组
     * @return 是否具有任意一个角色
     */
    public boolean isUserInAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return true;
        }
        
        for (String role : roles) {
            if (isUserInRole(role)) {
                log.debug("用户具有角色: {} - 用户: {}", role, remoteUser);
                return true;
            }
        }
        
        log.debug("用户不具有任何指定角色 - 用户: {}, 角色: {}", remoteUser, String.join(",", roles));
        return false;
    }

    /**
     * 检查用户是否具有指定权限
     * 
     * @param permission 权限名称
     * @return 是否具有该权限
     */
    public boolean hasPermission(String permission) {
        boolean hasPermission = principal != null && principal.hasPermission(permission);
        log.debug("检查用户权限 - 权限: {}, 结果: {}, 用户: {}", permission, hasPermission, remoteUser);
        return hasPermission;
    }

    /**
     * 检查用户是否具有任意一个指定权限
     * 
     * @param permissions 权限数组
     * @return 是否具有任意一个权限
     */
    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                log.debug("用户具有权限: {} - 用户: {}", permission, remoteUser);
                return true;
            }
        }
        
        log.debug("用户不具有任何指定权限 - 用户: {}, 权限: {}", remoteUser, String.join(",", permissions));
        return false;
    }

    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public String getUserId() {
        return principal != null ? principal.getUserId() : null;
    }

    /**
     * 获取租户ID
     * 
     * @return 租户ID
     */
    public String getTenantId() {
        return principal != null ? principal.getTenantId() : null;
    }

    /**
     * 获取部门ID
     * 
     * @return 部门ID
     */
    public String getDeptId() {
        return principal != null ? principal.getDeptId() : null;
    }

    /**
     * 检查用户是否为管理员
     * 
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return principal != null && principal.isAdmin();
    }

    /**
     * 获取OAuth Principal
     * 
     * @return OAuth Principal
     */
    public OAuthPrincipal getOAuthPrincipal() {
        return principal;
    }

    /**
     * 检查用户是否已认证
     * 
     * @return 是否已认证
     */
    public boolean isAuthenticated() {
        return principal != null && principal.isAuthenticated();
    }
}
