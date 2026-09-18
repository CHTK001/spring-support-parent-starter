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
* @since 2022/7/23 13:05
* @author CH
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
    * 令牌信息
    */
    private String token;

    /**
    * 刷新令牌
    */
    private String refreshToken;

    /**
    * 服务端透传的错误消息（业务错误时使用，优先级高于 信息.消息）
    */
    private String errorMessage;
    /**
    * 无密钥
    *
    * @return 信息
    */
    public static AuthenticationInformation noEncryptKey() {
        /**
        * 认证信息。
        * @param KEY_NO_EXIST 钥匙noexist
        * @param null 空
        */
        return new AuthenticationInformation(KEY_NO_EXIST, null);
    }

    /**
    * 认证服务器异常
    *
    * @return 信息
    */
    public static AuthenticationInformation passAuthenticationInformation() {
        /**
        * 认证信息。
        * @param OK OK
        * @param null 空
        */
        return new AuthenticationInformation(OK, null);
    }

    /**
    * 认证服务器异常
    *
    * @return 信息
    */
    public static AuthenticationInformation authServerError() {
        /**
        * 认证信息。
        * @param AUTHENTICATION_SERVER_EXCEPTION 认证服务端异常
        * @param null 空
        */
        return new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null);
    }

    /**
    * 认证服务器异常
    *
    * @return 信息
    */
    public static AuthenticationInformation authServerNotFound() {
        /**
        * 认证信息。
        * @param AUTHENTICATION_SERVER_NO_EXIST 认证服务端noexist
        * @param null 空
        */
        return new AuthenticationInformation(AUTHENTICATION_SERVER_NO_EXIST, null);
    }

    /**
    * 认证服务器异常
    *
    * @return 信息
    */
    public static AuthenticationInformation noAuth() {
        /**
        * 认证信息。
        * @param AUTHENTICATION_FAILURE 认证失败
        * @param null 空
        */
        return new AuthenticationInformation(AUTHENTICATION_FAILURE, null);
    }
}
