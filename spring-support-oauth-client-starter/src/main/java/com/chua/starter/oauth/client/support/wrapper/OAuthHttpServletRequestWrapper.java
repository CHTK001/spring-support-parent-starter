package com.chua.starter.oauth.client.support.wrapper;

import com.chua.starter.oauth.client.support.principal.OAuthPrincipal;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.security.Principal;
import java.util.Arrays;

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
        
        log.info("[请求包装]创建OAuth请求包装器 - 用户: {}, 认证类型: {}, 请求URI: {}", 
                remoteUser != null ? remoteUser : "匿名", 
                authType != null ? authType : "无", 
                request.getRequestURI());
        
        log.debug("[请求包装]详细信息 - 用户: {}, 认证类型: {}, 已认证: {}, 请求方法: {}, 远程地址: {}", 
                 remoteUser, authType, principal.isAuthenticated(),
                 request.getMethod(), request.getRemoteAddr());
        
        if (userResume != null) {
            log.debug("[请求包装]用户信息 - 用户ID: {}, 昵称: {}, 租户ID: {}, 部门ID: {}, 是否管理员: {}",
                     userResume.getUserId(),
                     userResume.getNickName(),
                     userResume.getTenantId(),
                     userResume.getDeptId(),
                     userResume.isAdmin());
        }
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
        log.info("[请求包装]创建已认证的请求包装器 - 用户: {}, 认证类型: {}",
                userResume != null ? userResume.getUsername() : "null",
                authType);
        return new OAuthHttpServletRequestWrapper(request, userResume, authType);
    }

    /**
     * 创建未认证的请求包装器
     * 
     * @param request 原始请求
     * @return 请求包装器
     */
    public static OAuthHttpServletRequestWrapper unauthenticated(HttpServletRequest request) {
        log.info("[请求包装]创建未认证的请求包装器 - 请求URI: {}", request.getRequestURI());
        return new OAuthHttpServletRequestWrapper(request, null, null);
    }

    @Override
    public String getAuthType() {
        log.debug("[Wrapper获取]获取认证类型: {}", authType);
        return authType;
    }

    @Override
    public String getRemoteUser() {
        log.debug("[Wrapper获取]获取远程用户: {}", remoteUser);
        return remoteUser;
    }

    @Override
    public Principal getUserPrincipal() {
        log.debug("[Wrapper获取]获取用户Principal - 用户: {}, 已认证: {}", 
                 principal != null ? principal.getName() : "null",
                 principal != null && principal.isAuthenticated());
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        boolean hasRole = principal != null && principal.hasRole(role);
        
        if (hasRole) {
            log.info("[Wrapper角色检查]通过 - 用户 [{}] 具有角色 [{}]", remoteUser, role);
        } else {
            log.info("[Wrapper角色检查]拒绝 - 用户 [{}] 不具有角色 [{}]", remoteUser, role);
        }
        
        log.debug("[Wrapper角色检查]详情 - 用户: {}, 检查角色: {}, 结果: {}, 用户所有角色: {}", 
                 remoteUser, role, hasRole ? "通过" : "拒绝",
                 principal != null ? principal.getRoles() : "无");
        
        return hasRole;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        log.info("[Wrapper登录]尝试执行登录操作 - 用户名: {}", username);
        log.debug("[Wrapper登录]登录请求详情 - 用户名: {}, 当前已认证用户: {}, 认证状态: {}",
                 username, remoteUser, principal != null && principal.isAuthenticated());
        
        // 检查是否已经登录
        if (principal != null && principal.isAuthenticated()) {
            log.warn("[Wrapper登录]登录失败 - 用户已经登录，无法重复登录 - 当前用户: {}, 尝试登录用户: {}", remoteUser, username);
            throw new ServletException("用户已经登录，无法重复登录");
        }
        
        // 这里可以集成实际的登录逻辑
        // 由于OAuth客户端通常通过外部认证服务器进行认证，
        // 这个方法主要用于记录和验证
        log.warn("[Wrapper登录]OAuth客户端不支持直接用户名密码登录，请使用OAuth认证流程");
        throw new ServletException("OAuth客户端不支持直接用户名密码登录，请使用OAuth认证流程");
    }

    @Override
    public void logout() throws ServletException {
        log.info("[Wrapper登出]开始执行登出操作 - 用户: {}, 用户ID: {}", remoteUser, getUserId());
        log.debug("[Wrapper登出]登出前状态 - 用户: {}, 认证类型: {}, 已认证: {}",
                 remoteUser, authType, principal != null && principal.isAuthenticated());
        
        try {
            // 清除Session中的用户信息
            HttpSession session = getSession(false);
            if (session != null) {
                String sessionId = session.getId();
                log.debug("[Wrapper登出]清除Session - SessionID: {}", sessionId);
                
                // 清除用户相关的Session属性
                session.removeAttribute("userResume");
                session.removeAttribute("userInfo");
                session.removeAttribute("principal");
                session.removeAttribute("authType");
                log.debug("[Wrapper登出]已清除Session属性: userResume, userInfo, principal, authType");
                
                // 可以选择是否完全销毁Session
                session.invalidate();
                log.debug("[Wrapper登出]Session已销毁 - SessionID: {}", sessionId);
                
                log.info("[Wrapper登出]登出成功 - 用户: {}, 已清除所有Session信息", remoteUser);
            } else {
                log.warn("[Wrapper登出]Session不存在，无需清理 - 用户: {}", remoteUser);
            }
            // 这里可以添加其他登出逻辑，比如：
            // 1. 通知OAuth服务器用户登出
            // 2. 清除缓存中的用户信息
            // 3. 记录登出日志
        } catch (Exception e) {
            log.error("[Wrapper登出]登出操作失败 - 用户: {}, 错误类型: {}, 错误信息: {}", 
                     remoteUser, e.getClass().getSimpleName(), e.getMessage(), e);
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
        String rolesStr = Arrays.toString(roles);
        log.debug("[Wrapper多角色检查]开始检查 - 用户: {}, 检查角色列表: {}", remoteUser, rolesStr);
        
        if (roles == null || roles.length == 0) {
            log.debug("[Wrapper多角色检查]角色列表为空，默认通过");
            return true;
        }
        
        for (String role : roles) {
            if (isUserInRole(role)) {
                log.info("[Wrapper多角色检查]通过 - 用户 [{}] 具有角色 [{}]", remoteUser, role);
                return true;
            }
        }
        
        log.info("[Wrapper多角色检查]拒绝 - 用户 [{}] 不具有角色列表 {} 中的任何一个", remoteUser, rolesStr);
        log.debug("[Wrapper多角色检查]详情 - 用户拥有角色: {}", principal != null ? principal.getRoles() : "无");
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
        
        if (hasPermission) {
            log.info("[Wrapper权限检查]通过 - 用户 [{}] 具有权限 [{}]", remoteUser, permission);
        } else {
            log.info("[Wrapper权限检查]拒绝 - 用户 [{}] 不具有权限 [{}]", remoteUser, permission);
        }
        
        log.debug("[Wrapper权限检查]详情 - 用户: {}, 检查权限: {}, 结果: {}", 
                 remoteUser, permission, hasPermission ? "通过" : "拒绝");
        
        return hasPermission;
    }

    /**
     * 检查用户是否具有任意一个指定权限
     * 
     * @param permissions 权限数组
     * @return 是否具有任意一个权限
     */
    public boolean hasAnyPermission(String... permissions) {
        String permissionsStr = Arrays.toString(permissions);
        log.debug("[Wrapper多权限检查]开始检查 - 用户: {}, 检查权限列表: {}", remoteUser, permissionsStr);
        
        if (permissions == null || permissions.length == 0) {
            log.debug("[Wrapper多权限检查]权限列表为空，默认通过");
            return true;
        }
        
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                log.info("[Wrapper多权限检查]通过 - 用户 [{}] 具有权限 [{}]", remoteUser, permission);
                return true;
            }
        }
        
        log.info("[Wrapper多权限检查]拒绝 - 用户 [{}] 不具有权限列表 {} 中的任何一个", remoteUser, permissionsStr);
        return false;
    }

    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public String getUserId() {
        String userId = principal != null ? principal.getUserId() : null;
        log.debug("[Wrapper获取]获取用户ID: {}", userId);
        return userId;
    }

    /**
     * 获取租户ID
     * 
     * @return 租户ID
     */
    public String getTenantId() {
        String tenantId = principal != null ? principal.getTenantId() : null;
        log.debug("[Wrapper获取]获取租户ID: {}", tenantId);
        return tenantId;
    }

    /**
     * 获取部门ID
     * 
     * @return 部门ID
     */
    public String getDeptId() {
        String deptId = principal != null ? principal.getDeptId() : null;
        log.debug("[Wrapper获取]获取部门ID: {}", deptId);
        return deptId;
    }

    /**
     * 检查用户是否为管理员
     * 
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        boolean result = principal != null && principal.isAdmin();
        log.debug("[Wrapper管理员检查]用户: {}, 是否管理员: {}", remoteUser, result ? "是" : "否");
        if (result) {
            log.info("[Wrapper管理员验证]用户 [{}] 是管理员", remoteUser);
        }
        return result;
    }

    /**
     * 获取OAuth Principal
     * 
     * @return OAuth Principal
     */
    public OAuthPrincipal getOAuthPrincipal() {
        log.debug("[Wrapper获取]获取OAuthPrincipal - 用户: {}, 已认证: {}", 
                 principal != null ? principal.getName() : "null",
                 principal != null && principal.isAuthenticated());
        return principal;
    }

    /**
     * 检查用户是否已认证
     * 
     * @return 是否已认证
     */
    public boolean isAuthenticated() {
        boolean result = principal != null && principal.isAuthenticated();
        log.debug("[Wrapper认证检查]用户: {}, 已认证: {}", remoteUser, result ? "是" : "否");
        return result;
    }
}
