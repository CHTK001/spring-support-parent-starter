package com.chua.common.support.excetion;

/**
 * 认证异常，表示用户身份验证失败时抛出的异常
 *
 * @author CH
 */
public class AuthenticationException extends RuntimeException {
    /**
     * 使用默认错误信息构造认证异常实例，默认消息为 "Authentication failed"
     */
    public AuthenticationException() {
        super("Authentication failed");
    }

    /**
     * 使用指定的错误信息构造认证异常实例
     *
     * @param message 详细的错误信息
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误信息和原始原因构造认证异常实例
     *
     * @param message 详细的错误信息
     * @param cause   导致该异常的根本原因
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
