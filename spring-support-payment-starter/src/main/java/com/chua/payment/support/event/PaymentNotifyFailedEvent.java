package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调处理失败事件
 */
@Getter
public class PaymentNotifyFailedEvent extends PaymentEvent {

    private final Long notifyLogId;
    private final Long notifyErrorId;
    private final String notifyType;
    private final String orderNo;
    private final String refundNo;
    private final String errorType;
    private final String errorMessage;

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
