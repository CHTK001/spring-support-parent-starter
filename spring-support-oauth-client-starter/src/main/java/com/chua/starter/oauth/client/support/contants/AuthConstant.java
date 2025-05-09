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
     * 机构
     */
    String DEPT = "dept";

    /**
     * 机构组织者
     */
    String DEPT_ORGANIZER = "dept:organizer";
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
    String OAUTH_UPGRADE_KEY_TOKEN = "x-oauth-upgrade-token";
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
     * 刷新令牌前缀
     */
    String TOKEN_REFRESH_PRE = "oauth:token:refresh:";
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
     * 是否为机构
     *
     * @param roles 角色
     * @return 是否为管理员
     */
    static boolean isDept(Set<String> roles) {
        return CollectionUtils.containsIgnoreCase(roles, DEPT);
    }

    /**
     * 是否为机构组织者(参与发布活动的合伙人)
     *
     * @param roles 角色
     * @return 是否为管理员
     */
    static boolean isDeptOrganizer(Set<String> roles) {
        return CollectionUtils.containsIgnoreCase(roles, DEPT) && CollectionUtils.containsIgnoreCase(roles, DEPT_ORGANIZER);
    }
    /**
     * 是否为运维
     *
     * @param roles 角色
     * @return 是否为管理员
     */

    static boolean isOps(Set<String> roles) {
        return CollectionUtils.containsIgnoreCase(roles, OPS);
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
