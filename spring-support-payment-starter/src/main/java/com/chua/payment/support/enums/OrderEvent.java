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
    * @param "发起支付" "发起支付"
    * @return 薪酬的结果
    */
    PAY("发起支付"),

    /**
    * 支付成功
    * @param "支付成功" "支付成功"
    * @return 薪酬成功的结果
    */
    PAY_SUCCESS("支付成功"),

    /**
    * 支付失败
    * @param "支付失败" "支付失败"
    * @return 薪酬失败的结果
    */
    PAY_FAIL("支付失败"),

    /**
    * 完成订单
    * @param "完成订单" "完成订单"
    * @return 完成的结果
    */
    COMPLETE("完成订单"),

    /**
    * 取消订单
    * @param "取消订单" "取消订单"
    * @return CANCEL的结果
    */
    CANCEL("取消订单"),

    /**
    * 支付超时
    * @param "支付超时" "支付超时"
    * @return 超时的结果
    */
    TIMEOUT("支付超时"),

    /**
    * 申请退款
    * @param "申请退款" "申请退款"
    * @return 退税的结果
    */
    REFUND("申请退款"),

    /**
    * 退款成功
    * @param "退款成功" "退款成功"
    * @return 退税成功的结果
    */
    REFUND_SUCCESS("退款成功"),

    /**
    * 退款失败
    * @param "退款失败" "退款失败"
    * @return 退税失败的结果
    */
    REFUND_FAIL("退款失败");

    /** 描述 */
    private final String description;

    /**
    * 订单事件。
    * @param description description
    */
    OrderEvent(String description) {
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
}
