package com.chua.starter.pay.support.exception;

/**
 * 创建订单失败
 * @author CH
 * @since 2024/12/30
 */
public class CreateOrderFailureException extends RuntimeException{

    public CreateOrderFailureException() {
        super("创建订单失败");
    }

    public CreateOrderFailureException(String message) {
        super(message);
    }
}
