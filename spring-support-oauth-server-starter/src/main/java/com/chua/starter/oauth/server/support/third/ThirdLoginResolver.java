package com.chua.starter.oauth.server.support.third;

import com.chua.starter.oauth.client.support.user.UserResult;

/**
 * 三方登录
 *
 * @author CH
 */
public interface ThirdLoginResolver {

    /**
     * 登录
     *
     * @param ticket 票据
     * @param appId  程序ID
     * @return 登录结果
     */
    UserResult login(String ticket, String appId);

}
