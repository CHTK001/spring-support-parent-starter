package com.chua.starter.oauth.client.support.exception;

import com.chua.common.support.core.exception.AuthenticationException;
import lombok.Getter;

/**
 * 鉴权异常
 *
 * @author CH
 * @since 2022/7/29 10:29
 */
@Getter
public class OauthException extends AuthenticationException {

    /**
     * 错误码
     */
    private int code = 401;

    public OauthException() {
    }

    public OauthException(String s) {
        super(s);
    }

    /**
     * 带错误码的构造函数
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public OauthException(int code, String message) {
        super(message);
        this.code = code;
    }
}
