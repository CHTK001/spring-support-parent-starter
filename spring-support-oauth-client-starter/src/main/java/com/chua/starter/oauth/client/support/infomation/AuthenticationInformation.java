package com.chua.starter.oauth.client.support.infomation;

import com.chua.starter.oauth.client.support.user.UserResume;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * 鉴权信息
 *
 * @author CH
 * @since 2022/7/23 13:05
 */
@Data
@RequiredArgsConstructor
public class AuthenticationInformation {
    /**
     * 状态信息
     */
    private final Information information;
    /**
     * 结果
     */
    private final UserResume returnResult;
    /**
     * token信息
     */
    private String token;

    /**
     * 刷新token
     */
    private String refreshToken;
    /**
     * 无密钥
     *
     * @return 信息
     */
    public static AuthenticationInformation noEncryptKey() {
        return new AuthenticationInformation(KEY_NO_EXIST, null);
    }

    /**
     * 认证服务器异常
     *
     * @return 信息
     */
    public static AuthenticationInformation passAuthenticationInformation() {
        return new AuthenticationInformation(OK, null);
    }

    /**
     * 认证服务器异常
     *
     * @return 信息
     */
    public static AuthenticationInformation authServerError() {
        return new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null);
    }

    /**
     * 认证服务器异常
     *
     * @return 信息
     */
    public static AuthenticationInformation authServerNotFound() {
        return new AuthenticationInformation(AUTHENTICATION_SERVER_NO_EXIST, null);
    }

    /**
     * 认证服务器异常
     *
     * @return 信息
     */
    public static AuthenticationInformation noAuth() {
        return new AuthenticationInformation(AUTHENTICATION_FAILURE, null);
    }
}
