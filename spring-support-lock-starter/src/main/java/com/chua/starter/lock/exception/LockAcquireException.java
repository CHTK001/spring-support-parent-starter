package com.chua.starter.lock.exception;

/**
 * 锁获取异常。
 *
 * @author CH
 * @since 2026-03-28
 */
public class LockAcquireException extends RuntimeException {

    public LockAcquireException(String message) {
        super(message);
    }

    public LockAcquireException(String message, Throwable cause) {
        super(message, cause);
    }
}
