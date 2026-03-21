package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调接收事件
 */
@Getter
public class PaymentNotifyReceivedEvent extends PaymentEvent {

    private final Long notifyLogId;
    private final String notifyType;
    private final String channelType;
    private final String channelSubType;
    private final String orderNo;
    private final String refundNo;
    private final Boolean signVerified;

    public PaymentNotifyReceivedEvent(Object source, Long tenantId, Long merchantId, Long notifyLogId,
                                      String notifyType, String channelType, String channelSubType,
                                      String orderNo, String refundNo, Boolean signVerified) {
        super(source, "PAYMENT_NOTIFY_RECEIVED", tenantId, merchantId);
        this.notifyLogId = notifyLogId;
        this.notifyType = notifyType;
        this.channelType = channelType;
        this.channelSubType = channelSubType;
        this.orderNo = orderNo;
        this.refundNo = refundNo;
        this.signVerified = signVerified;
    }
}
