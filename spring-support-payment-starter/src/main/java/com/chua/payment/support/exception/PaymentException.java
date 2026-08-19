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
     * 创建 PaymentException 实例
     * @param message message
     */
    public PaymentException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 创建 PaymentException 实例
     * @param code code
     * @param String String
     */
    public PaymentException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建 PaymentException 实例
     * @param message message
     * @param Throwable Throwable
     */
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    /**
     * 创建 PaymentException 实例
     * @param code code
     * @param String String
     * @param Throwable Throwable
     */
    public PaymentException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 获取Code */
    public Integer getCode() {
        return code;
    }

    /** 设置Code */
    public void setCode(Integer code) {
        this.code = code;
    }
}
