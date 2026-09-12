package com.chua.starter.rpc.support.proxy;

/**
* RPC 代理创建异常
*
* @版本 1.0.0
* @since 2024/04/07
* @author CH
 */
public class RpcProxyCreationException extends RuntimeException {

    /**
    * 创建 rpc代理创建异常 实例
    * @param message 消息
     */
    public RpcProxyCreationException(String message) {
        /**
        * 父。
        * @param message 消息
         */
        super(message);
    }

    /**
    * 创建 rpc代理创建异常 实例
    * @param message 消息
    * @param cause Throwable
    * @param cause cause
     */
    public RpcProxyCreationException(String message, Throwable cause) {
        /**
        * 父。
        * @param message 消息
        * @param cause cause
         */
        super(message, cause);
    }
}
