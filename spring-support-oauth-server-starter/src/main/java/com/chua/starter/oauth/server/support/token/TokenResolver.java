package com.chua.starter.oauth.server.support.token;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;

import jakarta.servlet.http.Cookie;

/**
 * 令牌解释器
 *
 * @author CH
 */
public interface TokenResolver {

    /**
     * 创建令牌
     *
     * @param address    地址
     * @param userResult 用户信息
     * @param authType   登录方式
     * @return 登录结果
     */
    ReturnResult<LoginResult> createToken(String address, UserResult userResult, String authType);

    /**
     * 注销
     *
     * @param token 注销
     */
    void logout(String token);

    /**
     * 注销
     *
     * @param uid  账号ID
     * @param type 类型
     */
    void logout(String uid, LogoutType type);

    /**
     * 解析token
     *
     * @param cookies cookie
     * @param token   token
     * @return 登录信息
     */
    ReturnResult<UserResult> resolve(Cookie[] cookies, String token);

    /**
     * 刷新token
     *
     * @param cookies cookie
     * @param token   token
     * @return 登录信息
     */
    ReturnResult<UserResult> refresh(Cookie[] cookies, String token);
}
