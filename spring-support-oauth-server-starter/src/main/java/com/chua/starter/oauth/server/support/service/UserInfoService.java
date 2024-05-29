package com.chua.starter.oauth.server.support.service;

import com.chua.starter.oauth.client.support.user.UserResult;

/**
 * 日志服务
 *
 * @author CH
 */
public interface UserInfoService {
    /**
     * 检验ak/sk是否合法
     *
     * @param username ak
     * @param password sk
     * @param address   地址
     * @param ext       额外参数
     * @return 检验ak/sk是否合法
     */
    UserResult checkLogin(String username, String password, String address, Object ext);

    /**
     * 获取用户信息
     *
     * @param userResult 账号信息
     * @return 用户信息
     */
    UserResult upgrade(UserResult userResult);
}
