package com.chua.common.support.excetion;

/**
 * 远程执行异常，表示远程调用过程中发生的异常
 *
 * @author CH
 * @since 4.0.0
 */
public class RemoteExecutionException extends RuntimeException {
    /**
      * 使用默认错误信息构造远程执行异常实例，默认消息为 "远程 执行 失败"
     */
    public RemoteExecutionException() {
        /**
         * 父。
         * @param failed" 失败"
         */
        super("Remote execution failed");
    }

    /**
     * 使用指定的错误信息构造远程执行异常实例
     *
     * @param message 详细的错误信息
     */
    public RemoteExecutionException(String message) {
        /**
         * 父。
         * @param message 消息
         */
        super(message);
    }

    /**
     * 使用指定的错误信息和原始原因构造远程执行异常实例
     *
     * @param message 详细的错误信息
     * @param cause   导致该异常的根本原因
     */
    public RemoteExecutionException(String message, Throwable cause) {
        /**
         * 父。
         * @param message 消息
         * @param cause cause
         */
        super(message, cause);
    }
}
