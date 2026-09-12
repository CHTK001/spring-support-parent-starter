package com.chua.payment.support.enums;

/**
* 退款单状态
* @author CH
* @since 4.0.0
 */
public enum RefundOrderStatus {

    /**
    * 处理。
    * @param "退款中" "退款中"
     */
    PROCESSING("退款中"),
    /**
    * 退税。
    * @param "已退款" "已退款"
     */
    REFUNDED("已退款"),
    /**
    * 失败。
    * @param "退款失败" "退款失败"
     */
    FAILED("退款失败");

    /** 描述 */
    private final String description;

    /**
    * 退税订单状态。
    * @param description description
     */
    RefundOrderStatus(String description) {
        this.description = description;
    }

    /**
    * 获取Description
    *
    * @return 获取description的结果
     */
    public String getDescription() {
        return description;
    }

    /**
    * description的
    *
    * @param code 编码
    * @return description的的结果
     */
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

