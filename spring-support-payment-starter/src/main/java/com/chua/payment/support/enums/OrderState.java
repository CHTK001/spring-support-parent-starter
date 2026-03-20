package com.chua.payment.support.enums;

/**
 * 订单状态枚举
 *
 * @author CH
 * @since 2026-03-18
 */
public enum OrderState {

    /**
     * 待支付
     */
    PENDING("待支付"),

    /**
     * 支付中
     */
    PAYING("支付中"),

    /**
     * 支付成功
     */
    PAID("支付成功"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 已取消
     */
    CANCELLED("已取消"),

    /**
     * 支付失败
     */
    FAILED("支付失败"),

    /**
     * 退款中
     */
    REFUNDING("退款中"),

    /**
     * 已退款
     */
    REFUNDED("已退款");

    private final String description;

    OrderState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static String descriptionOf(String code) {
        if (code == null) {
            return "";
        }
        for (OrderState value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value.description;
            }
        }
        return "";
    }
}
