package com.chua.starter.common.support.oauth;

/**
 * 权限服务
 * @author CH
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


    class DefaultAuthService implements AuthService {

        @Override
        public CurrentUser getCurrentUser() {
            return null;
        }
    }

}

