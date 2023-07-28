package com.chua.starter.mybatis.oauth;

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
}
