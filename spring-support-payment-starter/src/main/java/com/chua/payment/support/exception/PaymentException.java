package com.chua.payment.support.exception;

/**
 * 支付异常类
 *
 * @author CH
 * @since 2026-03-18
 */
public class PaymentException extends RuntimeException {

    /**
     * 错误码
     */
    private Integer code;

    public PaymentException(String message) {
        super(message);
        this.code = 500;
    }

    public PaymentException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public PaymentException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }
}
