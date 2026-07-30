package com.chua.starter.strategy.exception;

/**
 * 幂等性异常
 * <p>
 * 当检测到重复提交时抛出此异常
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class IdempotentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     *
     * @param message 错误信息
     */
    public IdempotentException(String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
