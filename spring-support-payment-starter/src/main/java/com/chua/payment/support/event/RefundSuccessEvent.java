package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 退款成功事件
 */
@Getter
public class RefundSuccessEvent extends PaymentEvent {

    private final Long orderId;
    private final String orderNo;
    private final Long refundId;
    private final String refundNo;
    private final Long userId;
    private final BigDecimal refundAmount;
    private final String thirdPartyRefundNo;
    private final String channelType;
    private final String channelSubType;

    public RefundSuccessEvent(Object source, Long tenantId, Long merchantId, Long orderId, String orderNo,
                              Long refundId, String refundNo, Long userId, BigDecimal refundAmount,
                              String thirdPartyRefundNo, String channelType, String channelSubType) {
        super(source, "REFUND_SUCCESS", tenantId, merchantId);
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.refundId = refundId;
        this.refundNo = refundNo;
        this.userId = userId;
        this.refundAmount = refundAmount;
        this.thirdPartyRefundNo = thirdPartyRefundNo;
        this.channelType = channelType;
        this.channelSubType = channelSubType;
    }
}
