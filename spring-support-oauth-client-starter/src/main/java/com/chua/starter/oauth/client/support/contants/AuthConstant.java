package com.chua.starter.oauth.client.support.contants;

import com.chua.common.support.utils.CollectionUtils;

import java.util.Set;

/**
 * 鉴权常量
 *
 * @author CH
 */
public interface AuthConstant {

    /**
     * 超级管理员
     */
    String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * ops
     */
    String OPS = "ops";
    /**
     * 管理员
     */
    String ADMIN = "ADMIN";
    /**
     * ak
     */
    String ACCESS_KEY = "x-access-key";
    /**
     * sk
     */
    String SECRET_KEY = "x-secret-key";
    /**
     * ok
     */
    String OAUTH_KEY = "x-oauth-key";


    /**
     * "x-oauth-upgrade"
     */
    String OAUTH_UPGRADE_KEY =  "x-oauth-upgrade";
    /**
     * ov
     */
    String OAUTH_VALUE = "x-oauth-value";

    /**
     * 鉴权
     */
    String OAUTH = "oauth";

    /**
     * 前缀
     */
    String LOGIN_ERROR = "oauth:error:";

    /**
     * 前缀
     */
    String TOKEN_PRE = "oauth:token:";
    /**
     * 令牌前缀
     */
    String PRE_KEY = "oauth:key:";

    /**
     * 是否为管理员
     *
     * @param roles 角色
     * @return 是否为管理员
     */
    static boolean isAdmin(Set<String> roles) {
        return CollectionUtils.containsIgnoreCase(roles, ADMIN) && !isSuperAdmin(roles);
    }

    /**
     * 是否为管理员
     *
     * @param roles 角色
     * @return 是否为管理员
     */
    static boolean hasAdmin(Set<String> roles) {
        return CollectionUtils.containsIgnoreCase(roles, ADMIN) || isSuperAdmin(roles);
    }
    /**
     * 是否为超级管理员
     *
     * @param roles 角色
     * @return 是否为超级管理员
     */
    static boolean isSuperAdmin(Set<String> roles) {
        return CollectionUtils.containsIgnoreCase(roles, SUPER_ADMIN);
    }
}
