// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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