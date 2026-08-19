package com.chua.starter.oauth.client.support.exception;

/**
 * OAuth异常
 *
 * @author CH
 */
public class OauthException extends RuntimeException {

    /**
     * 创建 OauthException 实例
     * @param message message
     */
    public OauthException(String message) {
        super(message);
    }
}
