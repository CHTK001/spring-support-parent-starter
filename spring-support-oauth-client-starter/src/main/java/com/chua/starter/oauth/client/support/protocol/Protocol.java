package com.chua.starter.oauth.client.support.protocol;

import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.Cookie;

import java.util.Map;

/**
 * Protocol接口定义了与认证和升级相关的操作。
 *
 * @author CH
 */
public interface Protocol {

    /**
     * 批准操作，用于处理认证信息。
     *
     * @param cookie      用于认证的Cookie数组。
     * @param token       用于认证的令牌字符串。
     * @param subProtocol 子协议名称。
     * @return 返回认证信息对象。
     */
    AuthenticationInformation approve(Cookie[] cookie, String token, String subProtocol);

    /**
     * 升级操作，用于处理会话升级请求。
     *
     * @param cookie       用于升级的Cookie数组。
     * @param token        用于升级的令牌字符串。
     * @param upgradeType  升级类型。
     * @param refreshToken 刷新令牌字符串。
     */
    LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken);

    /**
     * 获取访问令牌，用于根据用户凭据和认证类型进行登录认证。
     *
     * @param username   用户名。
     * @param password   密码。
     * @param authType   认证类型。
     * @param ext        扩展参数，可传递额外的信息。
     * @return 返回包含访问令牌的登录认证结果对象。
     */
    LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext);

    /**
     * 登出操作，用于根据用户ID、登出类型和用户信息执行会话终止。
     *
     * @param uid        用户唯一标识。
     * @param logoutType 登出类型。
     * @param userResult 用户信息结果对象。
     * @return 返回包含登出处理结果的LoginAuthResult对象。
     */
    LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult);
}
