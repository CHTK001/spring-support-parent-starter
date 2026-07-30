package com.chua.starter.strategy.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * 用户上下文工具类
 * <p>
 * 统一获取当前登录用户信息，通过请求属性、请求头、Session 获取。
 * 与 oauth-client-starter 配合使用时，oauth 会向 Session 写入 userId/username。
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
     * 1. 请求属性中的userId
     * 2. 请求头中的X-User-Id
     * 3. Session中的userId（oauth-client-starter 写入）
     * </p>
     *
     * @param request HTTP请求
     * @return 用户ID，如果无法获取则返回null
     */
    @Nullable
    public static String getUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. 尝试从请求属性获取
        Object attrUserId = request.getAttribute("userId");
        if (attrUserId != null) {
            return attrUserId.toString();
        }

        // 2. 尝试从请求头获取
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }

        // 3. 尝试从Session获取（oauth-client-starter 在此写入）
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
     * 获取用户名（如果可获取）
     *
     * @param request HTTP请求
     * @return 用户名，如果无法获取则返回null
     */
    @Nullable
    public static String getUsername(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. 尝试从请求属性获取
        Object attrUsername = request.getAttribute("username");
        if (attrUsername != null) {
            return attrUsername.toString();
        }

        // 2. 尝试从请求头获取
        String headerUsername = request.getHeader("X-User-Name");
        if (headerUsername != null && !headerUsername.isBlank()) {
            return headerUsername;
        }

        // 3. 尝试从Session获取（oauth-client-starter 在此写入）
        try {
            var session = request.getSession(false);
            if (session != null) {
                Object sessionAttr = session.getAttribute("username");
                if (sessionAttr != null) {
                    return sessionAttr.toString();
                }
            }
        } catch (Exception e) {
            log.debug("从Session获取用户名失败", e);
        }

        return null;
    }
}
