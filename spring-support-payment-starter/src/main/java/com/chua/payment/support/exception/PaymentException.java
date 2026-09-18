package com.chua.payment.support.exception;

/**
* 支付异常类
*
* @since 2026-03-18
* @author CH
 */
public class PaymentException extends RuntimeException {

    /**
    * 错误码
    */
    private Integer code;

    /**
    * 创建 薪酬异常 实例
    * @param message 消息
    */
    public PaymentException(String message) {
        /**
        * 父。
        * @param message 消息
        */
        super(message);
        this.code = 500;
    }

    /**
    * 创建 薪酬异常 实例
    * @param code 编码
    * @param message 字符串
    * @param message 消息
    */
    public PaymentException(Integer code, String message) {
        /**
        * 父。
        * @param message 消息
        */
        super(message);
        this.code = code;
    }

    /**
    * 创建 薪酬异常 实例
    * @param message 消息
    * @param cause Throwable
    * @param cause cause
    */
    public PaymentException(String message, Throwable cause) {
        /**
        * 父。
        * @param message 消息
        * @param cause cause
        */
        super(message, cause);
        this.code = 500;
    }

    /**
    * 创建 薪酬异常 实例
    * @param code 编码
    * @param message 字符串
    * @param cause Throwable
    * @param message 消息
    * @param cause cause
    */
    public PaymentException(Integer code, String message, Throwable cause) {
        /**
        * 父。
        * @param message 消息
        * @param cause cause
        */
        super(message, cause);
        this.code = code;
    }

    /**
    * 获取编码
    *
    * @return 获取编码的结果
    */
    public Integer getCode() {
        return code;
    }

    /**
    * 设置编码
    *
    * @param code 编码
    */
    public void setCode(Integer code) {
        this.code = code;
    }
}
