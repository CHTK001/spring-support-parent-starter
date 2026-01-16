package com.chua.starter.common.support.exception;

import com.chua.common.support.lang.code.ResultCode;
/**
 * 运行时消息异常
 * <p>
 * 用于封装运行时的业务消息异常，支持错误码和错误消息。
 * 通常用于需要向用户展示的友好错误提示。
 * </p>
 *
 * @author CH
 * @since 2022/7/31
 * @version 1.0.0
 */
public class RuntimeMessageException extends RuntimeException {

    /**
     * 业务错误码
     */
    private ResultCode resultCode;

    /**
     * 根据错误码构造异常
     *
     * @param errorCode 错误码对象
     */
    public RuntimeMessageException(ResultCode errorCode) {
        super(errorCode.getMsg());
        this.resultCode = errorCode;
    }

    /**
     * 根据错误消息构造异常
     *
     * @param message 错误消息
     */
    public RuntimeMessageException(String message) {
        super(message);
    }

    /**
     * 根据错误消息和原因构造异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public RuntimeMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 根据原因构造异常
     *
     * @param cause 原始异常
     */
    public RuntimeMessageException(Throwable cause) {
        super(cause);
    }
    /**
     * 获取 resultCode
     *
     * @return resultCode
     */
    public ResultCode getResultCode() {
        return resultCode;
    }



}

