package com.chua.starter.pay.support.constant;

/**
 * 支付常量
 * @author CH
 * @since 2025/10/14 14:01
 */
public interface PayConstant {

    /**
     * 创建订单前缀
     */
    String CREATE_ORDER_PREFIX = "pay:create:order:";
    /**
     * 转账订单前缀
     */
    String CREATE_TRANSFER_PREFIX = "pay:transfer:order:";
    /**
     * 退款订单前缀
     */
    String CREATE_REFUND_PREFIX = "pay:refund:order:";
}
