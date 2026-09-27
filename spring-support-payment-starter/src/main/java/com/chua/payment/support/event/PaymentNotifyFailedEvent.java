package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调处理失败事件
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
public class PaymentNotifyFailedEvent extends PaymentEvent {

    /**
     * 通知日志标识
     */
    private final Long notifyLogId;
    /**
     * 通知错误标识
     */
    private final Long notifyErrorId;
    /**
     * 通知类型
     */
    private final String notifyType;
    /**
     * 订单号
     */
    private final String orderNo;
    /**
     * 退款单号
     */
    private final String refundNo;
    /**
     * 错误类型
     */
    private final String errorType;
    /**
     * 错误消息
     */
    private final String errorMessage;

    /**
     * 创建支付通知失败事件实例
     *
     * @param source 来源
     * @param tenantId 租户标识
     * @param merchantId 商户标识
     * @param notifyLogId 通知日志标识
     * @param notifyErrorId 通知错误标识
     * @param notifyType 通知类型
     * @param orderNo 订单号
     * @param refundNo 退款单号
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     */
    public PaymentNotifyFailedEvent(Object source, Long tenantId, Long merchantId, Long notifyLogId,
                                   Long notifyErrorId, String notifyType, String orderNo, String refundNo,
                                   String errorType, String errorMessage) {
        super(source, "PAYMENT_NOTIFY_FAILED", tenantId, merchantId);
        this.notifyLogId = notifyLogId;
        this.notifyErrorId = notifyErrorId;
        this.notifyType = notifyType;
        this.orderNo = orderNo;
        this.refundNo = refundNo;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }
}
