package com.chua.starter.lock.exception;

/**
 * 幂等异常。
 *
 * @author CH
 * @since 2026-03-28
 */
public class IdempotentException extends RuntimeException {

    public IdempotentException(String message) {
        super(message);
    }

    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
