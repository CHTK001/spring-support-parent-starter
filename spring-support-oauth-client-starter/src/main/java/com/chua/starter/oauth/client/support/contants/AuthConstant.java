package com.chua.starter.oauth.client.support.contants;

import java.util.Set;

/**
 * 认证常量
 *
 * @author CH
 * @since 4.0.0
 */
public class AuthConstant {

    /** 父_admin */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Admin */
    public static final String ADMIN = "ADMIN";

    /** Ops */
    public static final String OPS = "OPS";

    /** Captcha_会话_钥匙 */
    public static final String CAPTCHA_SESSION_KEY = "captcha_session_key";

    /**
     * 访问令牌 Redis 键前缀
     */
    public static final String TOKEN_PRE = "oauth:token:";

    /**
     * 刷新令牌 Redis 键前缀
     */
    public static final String TOKEN_REFRESH_PRE = "oauth:refresh:";

    /**
     * 是否父admin
     *
     * @param roles 角色
     * @return 是否父admin的结果
     */
    public static boolean isSuperAdmin(Set<String> roles) {
        return roles != null && roles.contains(SUPER_ADMIN);
    }

    /**
     * 是否Admin
     *
     * @param roles 角色
     * @return 是否admin的结果
     */
    public static boolean isAdmin(Set<String> roles) {
        return roles != null && (roles.contains(SUPER_ADMIN) || roles.contains("ADMIN"));
    }
}
