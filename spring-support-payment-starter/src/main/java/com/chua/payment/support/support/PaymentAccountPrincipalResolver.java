package com.chua.payment.support.support;

import com.chua.starter.oauth.client.support.principal.OAuthPrincipal;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.wrapper.OAuthHttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Locale;
import java.util.Set;
/**
 * 从 HttpServletRequest 中解析当前登录账户主体的工具类。
 *
 * @author CH
 * @since 4.0.0.50
 */

@Component
public class PaymentAccountPrincipalResolver {

    /** 解析 */
    public UserResume resolve(HttpServletRequest request) {
        if (request instanceof OAuthHttpServletRequestWrapper wrapper && wrapper.isAuthenticated()) {
            OAuthPrincipal principal = wrapper.getOAuthPrincipal();
            return principal != null ? principal.getUserResume() : null;
        }
        Principal principal = request.getUserPrincipal();
        if (principal instanceof OAuthPrincipal oauthPrincipal && oauthPrincipal.isAuthenticated()) {
            return oauthPrincipal.getUserResume();
        }
        return null;
    }

    /** 解析UserId */
    public Long resolveUserId(HttpServletRequest request) {
        return parseUserId(resolve(request));
    }

    /** 解析UserId */
    public Long parseUserId(UserResume userResume) {
        if (userResume == null || !StringUtils.hasText(userResume.getUserId())) {
            return null;
        }
        try {
            return Long.parseLong(userResume.getUserId().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 是否Admin */
    public boolean isAdmin(UserResume userResume) {
        if (userResume == null) {
            return false;
        }
        Set<String> roles = userResume.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().map(it -> it == null ? "" : it.trim().toUpperCase(Locale.ROOT))
                .anyMatch(it -> "ADMIN".equals(it) || "SUPER_ADMIN".equals(it) || "SUPERADMIN".equals(it));
    }
}

