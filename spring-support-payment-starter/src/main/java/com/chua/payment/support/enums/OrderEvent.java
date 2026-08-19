package com.chua.payment.support.enums;

/**
 * 订单事件枚举
 *
 * @since 2026-03-18
 * @author CH
 */
public enum OrderEvent {

    /**
     * 发起支付
     */
    PAY("发起支付"),

    /**
     * 支付成功
     */
    PAY_SUCCESS("支付成功"),

    /**
     * 支付失败
     */
    PAY_FAIL("支付失败"),

    /**
     * 完成订单
     */
    COMPLETE("完成订单"),

    /**
     * 取消订单
     */
    CANCEL("取消订单"),

    /**
     * 支付超时
     */
    TIMEOUT("支付超时"),

    /**
     * 申请退款
     */
    REFUND("申请退款"),

    /**
     * 退款成功
     */
    REFUND_SUCCESS("退款成功"),

    /**
     * 退款失败
     */
    REFUND_FAIL("退款失败");

    /** 描述 */
    private final String description;

    OrderEvent(String description) {
        this.description = description;
    }

    /** 获取Description */
    public String getDescription() {
        return description;
    }
}
