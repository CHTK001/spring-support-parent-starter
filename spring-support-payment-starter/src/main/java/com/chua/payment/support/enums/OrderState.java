package com.chua.payment.support.enums;

/**
 * 订单状态枚举
 *
 * @since 2026-03-18
 * @author CH
 */
public enum OrderState {

    /**
     * 待支付
     * @param "待支付" "待支付"
     * @return PENDING的结果
     */
    PENDING("待支付"),

    /**
     * 支付中
     * @param "支付中" "支付中"
     * @return 薪酬的结果
     */
    PAYING("支付中"),

    /**
     * 支付成功
     * @param "支付成功" "支付成功"
     * @return PAID的结果
     */
    PAID("支付成功"),

    /**
     * 已完成
     * @param "已完成" "已完成"
     * @return 完成的结果
     */
    COMPLETED("已完成"),

    /**
     * 已取消
     * @param "已取消" "已取消"
     * @return CANCELLED的结果
     */
    CANCELLED("已取消"),

    /**
     * 支付超时
     * @param "支付超时" "支付超时"
     * @return 超时的结果
     */
    TIMEOUT("支付超时"),

    /**
     * 支付失败
     * @param "支付失败" "支付失败"
     * @return 失败的结果
     */
    FAILED("支付失败"),

    /**
     * 退款中
     * @param "退款中" "退款中"
     * @return 退税的结果
     */
    REFUNDING("退款中"),

    /**
     * 已退款
     * @param "已退款" "已退款"
     * @return 退税的结果
     */
    REFUNDED("已退款");

    /**
     * 描述
    */
    private final String description;

    /**
     * 订单状态。
     * @param description description
     */
    OrderState(String description) {
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
        for (OrderState value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value.description;
            }
        }
        return "";
    }
}
