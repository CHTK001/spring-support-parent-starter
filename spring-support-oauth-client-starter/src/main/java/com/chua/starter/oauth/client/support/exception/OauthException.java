package com.chua.starter.oauth.client.support.exception;

import com.chua.common.support.lang.exception.AuthenticationException;

/**
 * 鉴权异常
 *
 * @author CH
 * @since 2022/7/29 10:29
 */
public class OauthException extends AuthenticationException {

    public OauthException() {
    }

    public OauthException(String s) {
        super(s);
    }
}
