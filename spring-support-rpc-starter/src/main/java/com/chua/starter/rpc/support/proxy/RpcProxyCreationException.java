package com.chua.starter.rpc.support.proxy;

/**
 * RPC 代理创建异常
 *
 * @version 1.0.0
 * @since 2024/04/07
 * @author CH
 */
public class RpcProxyCreationException extends RuntimeException {

    /**
     * 创建 RpcProxyCreationException 实例
     * @param message message
     */
    public RpcProxyCreationException(String message) {
        super(message);
    }

    /**
     * 创建 RpcProxyCreationException 实例
     * @param message message
     * @param Throwable Throwable
     */
    public RpcProxyCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
