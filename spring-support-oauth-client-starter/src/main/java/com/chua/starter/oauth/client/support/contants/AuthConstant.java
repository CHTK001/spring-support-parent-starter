package com.chua.starter.oauth.client.support.contants;

import java.util.Set;

/**
 * 认证常量
 *
 * @author CH
 */
public class AuthConstant {

    /** Super_admin */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Admin */
    public static final String ADMIN = "ADMIN";

    /** Ops */
    public static final String OPS = "OPS";

    /** Captcha_session_key */
    public static final String CAPTCHA_SESSION_KEY = "captcha_session_key";

    /**
     * 访问令牌 Redis 键前缀
     */
    public static final String TOKEN_PRE = "oauth:token:";

    /**
     * 刷新令牌 Redis 键前缀
     */
    public static final String TOKEN_REFRESH_PRE = "oauth:refresh:";

    /** 是否SuperAdmin */
    public static boolean isSuperAdmin(Set<String> roles) {
        return roles != null && roles.contains(SUPER_ADMIN);
    }

    /** 是否Admin */
    public static boolean isAdmin(Set<String> roles) {
        return roles != null && (roles.contains(SUPER_ADMIN) || roles.contains("ADMIN"));
    }
}
