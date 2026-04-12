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

@Component
public class PaymentAccountPrincipalResolver {

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

    public Long resolveUserId(HttpServletRequest request) {
        return parseUserId(resolve(request));
    }

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
