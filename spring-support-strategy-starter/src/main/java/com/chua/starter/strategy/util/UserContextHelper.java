package com.chua.starter.strategy.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文工具类
 * <p>
 * 统一获取当前登录用户信息，支持Spring Security和自定义方式。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
public class UserContextHelper {

    /**
     * 从请求中获取用户ID
     * <p>
     * 优先级：
     * 1. Spring Security Authentication
     * 2. 请求属性中的userId
     * 3. 请求头中的userId
     * 4. Session中的userId
     * </p>
     *
     * @param request HTTP请求
     * @return 用户ID，如果无法获取则返回null
     */
    @Nullable
    public static String getUserId(HttpServletRequest request) {
        // 1. 尝试从Spring Security获取
        String userId = getUserIdFromSecurityContext();
        if (userId != null) {
            return userId;
        }

        // 2. 尝试从请求属性获取
        Object attrUserId = request.getAttribute("userId");
        if (attrUserId != null) {
            return attrUserId.toString();
        }

        // 3. 尝试从请求头获取
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }

        // 4. 尝试从Session获取
        try {
            var session = request.getSession(false);
            if (session != null) {
                Object sessionAttr = session.getAttribute("userId");
                if (sessionAttr != null) {
                    return sessionAttr.toString();
                }
            }
        } catch (Exception e) {
            log.debug("从Session获取用户ID失败", e);
        }

        return null;
    }

    /**
     * 从Spring Security上下文获取用户ID
     *
     * @return 用户ID，如果无法获取则返回null
     */
    @Nullable
    private static String getUserIdFromSecurityContext() {
        try {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            if (securityContext == null) {
                return null;
            }

            Authentication authentication = securityContext.getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            if (principal == null) {
                return null;
            }

            // 如果principal是字符串，直接返回
            if (principal instanceof String) {
                return (String) principal;
            }

            // 如果principal有getId方法，尝试调用
            try {
                var getIdMethod = principal.getClass().getMethod("getId");
                Object id = getIdMethod.invoke(principal);
                if (id != null) {
                    return id.toString();
                }
            } catch (Exception e) {
                log.debug("principal没有getId方法或调用失败", e);
            }

            // 如果principal有getUserId方法，尝试调用
            try {
                var getUserIdMethod = principal.getClass().getMethod("getUserId");
                Object userId = getUserIdMethod.invoke(principal);
                if (userId != null) {
                    return userId.toString();
                }
            } catch (Exception e) {
                log.debug("principal没有getUserId方法或调用失败", e);
            }

            // 如果principal有getUsername方法，尝试调用（作为备选）
            try {
                var getUsernameMethod = principal.getClass().getMethod("getUsername");
                Object username = getUsernameMethod.invoke(principal);
                if (username != null) {
                    return username.toString();
                }
            } catch (Exception e) {
                log.debug("principal没有getUsername方法或调用失败", e);
            }

            // 最后尝试toString
            return principal.toString();
        } catch (Exception e) {
            log.debug("从SecurityContext获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 获取用户名（如果可获取）
     *
     * @param request HTTP请求
     * @return 用户名，如果无法获取则返回null
     */
    @Nullable
    public static String getUsername(HttpServletRequest request) {
        try {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            if (securityContext != null) {
                Authentication authentication = securityContext.getAuthentication();
                if (authentication != null && authentication.isAuthenticated()) {
                    Object principal = authentication.getPrincipal();
                    if (principal != null) {
                        // 尝试获取username
                        try {
                            var getUsernameMethod = principal.getClass().getMethod("getUsername");
                            Object username = getUsernameMethod.invoke(principal);
                            if (username != null) {
                                return username.toString();
                            }
                        } catch (Exception ignored) {
                        }

                        // 如果principal是字符串，作为用户名返回
                        if (principal instanceof String) {
                            return (String) principal;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取用户名失败", e);
        }

        return null;
    }
}

