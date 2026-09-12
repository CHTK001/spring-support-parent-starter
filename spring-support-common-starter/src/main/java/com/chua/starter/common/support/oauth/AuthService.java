package com.chua.starter.common.support.oauth;

/**
 * 权限服务
 * @author CH
 * @since 4.0.0
 */
public interface AuthService {
    /**
     * 获取当前登录账号
     * @return 获取当前登录账号
     */
    CurrentUser getCurrentUser();

    /**
     * 是否已认证
     *
     * @return 是否已认证
     */
    default boolean isAuthenticated() {
        return getCurrentUser() != null;
    }


    /**
     * 默认认证服务类。
     *
     * @author CH
     * @since 4.0.0
     */
    class DefaultAuthService implements AuthService {

        @Override
        /** 获取当前用户 */
        public CurrentUser getCurrentUser() {
            return null;
        }
    }

}

