package com.chua.starter.rpc.support.exception;

/**
 * RPC 服务异常
 * <p>用于 RPC 服务注册、发现等操作时的异常</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
public class RpcServiceException extends RuntimeException {

    public RpcServiceException(String message) {
        super(message);
    }

    public RpcServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcServiceException(String interfaceName, String operation, Throwable cause) {
        super(String.format("RPC service operation '%s' failed for interface [%s]", operation, interfaceName), cause);
    }
}
