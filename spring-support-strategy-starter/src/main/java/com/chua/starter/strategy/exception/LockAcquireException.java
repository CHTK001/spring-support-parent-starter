package com.chua.starter.strategy.exception;

/**
 * 锁获取异常
 * <p>
 * 当分布式锁获取失败时抛出此异常
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class LockAcquireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     *
     * @param message 错误信息
     */
    public LockAcquireException(String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public LockAcquireException(String message, Throwable cause) {
        super(message, cause);
    }
}
