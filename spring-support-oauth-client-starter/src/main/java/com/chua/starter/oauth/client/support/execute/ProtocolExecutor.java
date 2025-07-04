package com.chua.starter.oauth.client.support.execute;


import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;

import java.util.Map;

/**
 * ProtocolExecutor接口定义了协议执行器的行为。
 * 它旨在提供一种标准化的方式，来执行特定的协议操作。
 * @author CH
 * @since 2024/6/12
 */
public interface ProtocolExecutor {


    /**
     * 获取访问令牌
     *
     * 本函数旨在通过用户提供的认证信息（用户名、密码）和认证类型，从认证服务器获取访问令牌。
     * 认证信息的详细处理方式依赖于认证类型（例如，OAuth2.0、LDAP等）。
     * 此外，函数还允许传递额外的参数，以支持不同认证类型的特定需求。
     *
     * @param username 用户名，用于身份验证
     * @param password 密码，用于身份验证
     * @param authType 认证类型，定义了使用的认证协议和流程
     * @param ext 额外参数，以键值对形式提供，用于支持特定认证类型的额外需求
     * @return LoginAuthResult 对象，包含获取的访问令牌和其他相关信息
     */
    LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext);
    /**
     * 用户登出接口。
     *
     * 该方法用于处理用户的登出操作，根据不同的登出类型，执行相应的登出逻辑。
     * 对于单设备登出或全局登出，需要在后端进行相应的用户会话失效处理，以确保用户安全。
     *
     * @param uid 用户唯一标识。用于确定要登出的用户。
     * @param logoutType 登出类型。定义了登出操作的范围，例如仅登出当前设备或全局登出。
     * @param userResult 用户信息
     * @return LoginAuthResult 登出操作的结果。可能包含操作是否成功、失败原因等信息。
     */
    LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult );
}
