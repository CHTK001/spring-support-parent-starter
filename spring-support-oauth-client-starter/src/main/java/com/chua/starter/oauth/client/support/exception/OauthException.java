package com.chua.starter.oauth.client.support.exception;

/**
   * o认证异常
 *
 * @author CH
 * @since 4.0.0
 */
public class OauthException extends RuntimeException {

    /**
       * 创建 OAuth异常 实例
     * @param message 消息
     */
    public OauthException(String message) {
        /**
         * 父。
         * @param message 消息
         */
        super(message);
    }
}
