package com.chua.payment.support.enums;

/**
 * 退款单状态
 * @author CH
 */
public enum RefundOrderStatus {

    PROCESSING("退款中"),
    REFUNDED("已退款"),
    FAILED("退款失败");

    /** 描述 */
    private final String description;

    RefundOrderStatus(String description) {
        this.description = description;
    }

    /** 获取Description */
    public String getDescription() {
        return description;
    }

    /** DescriptionOf */
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

