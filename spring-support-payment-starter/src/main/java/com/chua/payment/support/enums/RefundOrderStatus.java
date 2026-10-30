package com.chua.payment.support.enums;

/**
 * 退款单状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum RefundOrderStatus {

    /**
     * 处理中，退款已提交渠道等待结果
     */
    PROCESSING("退款中"),
    /**
     * 退款成功，款项已退回原支付账户
     */
    REFUNDED("已退款"),
    /**
     * 退款失败，渠道拒绝了本次退款
     */
    FAILED("退款失败");

    /**
     * 描述
     */
    private final String description;

    /**
     * 退款订单状态
     * @param description 描述
     * @return 返回值
    */
    RefundOrderStatus(String description) {
        this.description = description;
    }

    /**
     * 获取描述
     *
     * @return 获取描述的结果
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据状态编码获取描述
     *
     * @param code 编码
     * @return 描述
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
