package com.chua.starter.oauth.server.support.gitee;

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
     * 认证用户
     *
     * @param data  数据
     * @param token
     */
    void binder(AuthUser data, String token);
}
