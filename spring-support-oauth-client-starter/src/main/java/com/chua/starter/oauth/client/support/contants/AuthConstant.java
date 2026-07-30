package com.chua.starter.oauth.client.support.contants;

import java.util.Set;

/**
 * 认证常量
 *
 * @author CH
 */
public class AuthConstant {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    public static final String ADMIN = "ADMIN";

    public static final String CAPTCHA_SESSION_KEY = "captcha_session_key";

    public static boolean isSuperAdmin(Set<String> roles) {
        return roles != null && roles.contains(SUPER_ADMIN);
    }

    public static boolean isAdmin(Set<String> roles) {
        return roles != null && (roles.contains(SUPER_ADMIN) || roles.contains("ADMIN"));
    }
}
