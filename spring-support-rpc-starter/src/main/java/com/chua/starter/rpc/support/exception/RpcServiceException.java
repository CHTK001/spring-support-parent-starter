package com.chua.starter.rpc.support.exception;

/**
* RPC 服务异常
* <p>用于 RPC 服务注册、发现等操作时的异常</p>
*
* @版本 1.0.0
* @since 2024/04/07
* @author CH
 */
public class RpcServiceException extends RuntimeException {

    /**
    * 创建 rpc服务异常 实例
    * @param message 消息
    */
    public RpcServiceException(String message) {
        /**
        * 父。
        * @param message 消息
        */
        super(message);
    }

    /**
    * 创建 rpc服务异常 实例
    * @param message 消息
    * @param cause Throwable
    * @param cause cause
    */
    public RpcServiceException(String message, Throwable cause) {
        /**
        * 父。
        * @param message 消息
        * @param cause cause
        */
        super(message, cause);
    }

    /**
    * 创建 rpc服务异常 实例
    * @param interfaceName 接口名称
    * @param interfaceName 字符串
    * @param cause Throwable
    * @param operation operation
    * @param cause cause
    */
    public RpcServiceException(String interfaceName, String operation, Throwable cause) {
        /**
        * 父。
        * @param interfaceName 接口名称
        */
        super(String.format("RPC service operation '%s' failed for interface [%s]", operation, interfaceName), cause);
    }
}
