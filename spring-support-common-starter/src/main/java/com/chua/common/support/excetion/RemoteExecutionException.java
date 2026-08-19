package com.chua.common.support.excetion;

/**
 * 远程执行异常，表示远程调用过程中发生的异常
 *
 * @author CH
 */
public class RemoteExecutionException extends RuntimeException {
    /**
     * 使用默认错误信息构造远程执行异常实例，默认消息为 "Remote execution failed"
     */
    public RemoteExecutionException() {
        super("Remote execution failed");
    }

    /**
     * 使用指定的错误信息构造远程执行异常实例
     *
     * @param message 详细的错误信息
     */
    public RemoteExecutionException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误信息和原始原因构造远程执行异常实例
     *
     * @param message 详细的错误信息
     * @param cause   导致该异常的根本原因
     */
    public RemoteExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
