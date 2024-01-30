package com.chua.starter.oauth.server.support.gitee;

import com.chua.starter.oauth.client.support.user.UserResult;
import me.zhyd.oauth.model.AuthUser;

/**
 * gitee服务
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/29
 */
public interface GiteeService {

    /**
     * 粘合剂
     * 认证用户
     *
     * @param data       数据
     * @param userResult token
     */
    void binder(AuthUser data, UserResult userResult);


    /**
     * 进行登录
     *
     * @param data 数据
     * @return boolean
     */
    boolean doLogin(AuthUser data);
}
