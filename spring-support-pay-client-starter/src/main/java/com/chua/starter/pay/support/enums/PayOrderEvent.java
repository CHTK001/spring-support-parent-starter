package com.chua.starter.pay.support.enums;

/**
 * 订单状态机事件枚举
 * 
 * @author CH
 * @version 1.0
 * @since 2025/10/24
 */
public enum PayOrderEvent {

    /**
     * 创建订单
     */
    CREATE,

    /**
     * 创建失败
     */
    CREATE_FAILED,

    /**
     * 等待支付
     */
    WAIT_PAY,

    /**
     * 开始支付
     */
    START_PAY,

    /**
     * 支付成功
     */
    PAY_SUCCESS,

    /**
     * 订单超时
     */
    TIMEOUT,

    /**
     * 取消订单
     */
    CANCEL,

    /**
     * 关闭订单
     */
    CLOSE,

    /**
     * 申请退款
     */
    REFUND,

    /**
     * 退款成功
     */
    REFUND_SUCCESS,

    /**
     * 部分退款
     */
    REFUND_PART_SUCCESS
}

