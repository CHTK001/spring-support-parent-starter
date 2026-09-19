package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.web.WebRequest;

import java.util.Map;

/**
 * 实现了协议执行器接口的静态协议执行器类。
 * <p>
 * 该类旨在处理特定的协议执行逻辑，通过静态方法提供协议处理能力。
 * 作为协议执行器，它负责解析和执行特定格式的协议数据。
 * </p>
 * @since 2024/6/12
 * @author CH
 */
@Spi("default")
public class DefaultProtocolExecutor implements ProtocolExecutor{

    /**
     * 认证客户端属性
    */
    private final AuthClientProperties authClientProperties;

    /**
     * 创建 默认协议执行器 实例
    */
    public DefaultProtocolExecutor() {
        this.authClientProperties = OauthClientRuntimeContext.getAuthClientProperties();
    }

    @Override
    /**
     * 获取access令牌
    */
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return WebRequest.getProtocolExtension(authClientProperties)
                .getAccessToken(username, password, authType, ext);
    }

    @Override
    /**
     * Logout
    */
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult ) {
        return WebRequest.getProtocolExtension(authClientProperties)
                .logout(uid, logoutType, userResult);
    }

}