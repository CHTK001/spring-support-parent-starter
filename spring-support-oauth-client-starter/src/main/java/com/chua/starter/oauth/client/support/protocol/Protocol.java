package com.chua.starter.oauth.client.support.protocol;

import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.user.LoginResult;
import jakarta.servlet.http.Cookie;

/**
 * Protocol接口定义了与认证和升级相关的操作。
 * @author CH
 */
public interface Protocol {

    /**
     * 批准操作，用于处理认证信息。
     * @param cookie 用于认证的Cookie数组。
     * @param token 用于认证的令牌字符串。
     * @return 返回认证信息对象。
     */
    AuthenticationInformation approve(Cookie[] cookie, String token);

    /**
     * 升级操作，用于处理会话升级请求。
     *
     * @param cookie       用于升级的Cookie数组。
     * @param token        用于升级的令牌字符串。
     * @param upgradeType  升级类型。
     * @param refreshToken
     */
    LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken);
}
