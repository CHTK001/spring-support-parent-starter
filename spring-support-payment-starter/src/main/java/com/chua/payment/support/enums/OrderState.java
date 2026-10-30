package com.chua.payment.support.enums;

/**
 * 订单状态枚举
 *
 * @since 2026-03-18
 * @author CH
 */
public enum OrderState {

    /**
     * 待支付，订单已创建但未发起收款
     */
    PENDING("待支付"),

    /**
     * 支付中，已向渠道发起收款，等待渠道回调
     */
    PAYING("支付中"),

    /**
     * 支付成功，渠道已确认收款
     */
    PAID("支付成功"),

    /**
     * 已完成，订单履约结束
     */
    COMPLETED("已完成"),

    /**
     * 已取消，订单被主动关闭
     */
    CANCELLED("已取消"),

    /**
     * 支付超时，超过有效期未完成支付
     */
    TIMEOUT("支付超时"),

    /**
     * 支付失败，收款未成功
     */
    FAILED("支付失败"),

    /**
     * 退款中，已发起退款等待渠道处理
     */
    REFUNDING("退款中"),

    /**
     * 已退款，退款已成功到账
     */
    REFUNDED("已退款");

    /**
     * 描述
     */
    private final String description;

    /**
     * 订单状态
     * @param description 描述
     * @return 返回值
    */
    OrderState(String description) {
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
        for (OrderState value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value.description;
            }
        }
        return "";
    }
}
