package com.chua.starter.rpc.support.proxy;

/**
 * RPC 代理创建异常
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
public class RpcProxyCreationException extends RuntimeException {

    public RpcProxyCreationException(String message) {
        super(message);
    }

    public RpcProxyCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
