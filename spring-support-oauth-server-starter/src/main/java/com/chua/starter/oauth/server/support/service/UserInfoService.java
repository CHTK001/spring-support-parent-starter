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
     * @param accessKey ak
     * @param secretKey sk
     * @param address   地址
     * @return 检验ak/sk是否合法
     */
    UserResult checkLogin(String accessKey, String secretKey, String address);
}
