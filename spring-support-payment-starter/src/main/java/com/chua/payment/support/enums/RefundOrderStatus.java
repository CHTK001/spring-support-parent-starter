package com.chua.payment.support.enums;

/**
 * 退款单状态
 */
public enum RefundOrderStatus {

    PROCESSING("退款中"),
    REFUNDED("已退款"),
    FAILED("退款失败");

    private final String description;

    RefundOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static String descriptionOf(String code) {
        if (code == null) {
            return "";
        }
        for (RefundOrderStatus value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value.description;
            }
        }
        return "";
    }
}
