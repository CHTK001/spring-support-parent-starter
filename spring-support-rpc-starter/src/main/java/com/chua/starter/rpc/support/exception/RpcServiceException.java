package com.chua.starter.rpc.support.exception;

/**
 * RPC 服务异常
 * <p>用于 RPC 服务注册、发现等操作时的异常</p>
 *
 * @version 1.0.0
 * @since 2024/04/07
 * @author CH
 */
public class RpcServiceException extends RuntimeException {

    /**
     * 创建 RpcServiceException 实例
     * @param message message
     */
    public RpcServiceException(String message) {
        super(message);
    }

    /**
     * 创建 RpcServiceException 实例
     * @param message message
     * @param Throwable Throwable
     */
    public RpcServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建 RpcServiceException 实例
     * @param interfaceName interfaceName
     * @param String String
     * @param Throwable Throwable
     */
    public RpcServiceException(String interfaceName, String operation, Throwable cause) {
        super(String.format("RPC service operation '%s' failed for interface [%s]", operation, interfaceName), cause);
    }
}
