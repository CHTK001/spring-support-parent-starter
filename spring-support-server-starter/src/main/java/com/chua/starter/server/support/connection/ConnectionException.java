package com.chua.starter.server.support.connection;

import java.io.IOException;

/**
 * 连接管理异常
 * <p>
 * 连接缓存管理器在建立 / 复用 SSH 连接失败时抛出。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ConnectionException extends IOException {

    /**
     * 构造
     *
     * @param message 异常消息
     * @param cause   根因
     */
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造
     *
     * @param message 异常消息
     */
    public ConnectionException(String message) {
        super(message);
    }
}
