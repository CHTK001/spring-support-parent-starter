package com.chua.starter.common.support.exception;

import com.chua.common.support.lang.code.ResultCode;
/**
* 自定义业务异常
* <p>
* 用于封装业务逻辑中的异常情况，支持错误码和错误消息。
* 可被全局异常处理器捕获并返回统一的错误响应。
* </p>
*
* @since 2022/7/31
* @版本 1.0.0
* @author CH
 */
public class BusinessException extends RuntimeException {

    /**
    * 业务错误码
    */
    private ResultCode resultCode;

    /**
    * 根据错误码构造业务异常
    *
    * @param errorCode 错误码对象
    */
    public BusinessException(ResultCode errorCode) {
        /**
        * 父。
        * @param errorCode.getMsg( 错误编码.getmsg(
        */
        super(errorCode.getMsg());
        this.resultCode = errorCode;
    }

    /**
    * 根据错误消息构造业务异常
    *
    * @param message 错误消息
    */
    public BusinessException(String message) {
        /**
        * 父。
        * @param message 消息
        */
        super(message);
    }

    /**
    * 根据错误消息和原因构造业务异常
    *
    * @param message 错误消息
    * @param cause   原始异常
    */
    public BusinessException(String message, Throwable cause) {
        /**
        * 父。
        * @param message 消息
        * @param cause cause
        */
        super(message, cause);
    }

    /**
    * 根据原因构造业务异常
    *
    * @param cause 原始异常
    */
    public BusinessException(Throwable cause) {
        /**
        * 父。
        * @param cause cause
        */
        super(cause);
    }
    /**
    * 获取 结果编码
    *
    * @return resultCode
    */
    public ResultCode getResultCode() {
        return resultCode;
    }



}

