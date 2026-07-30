package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 支付成功事件
 */
@Getter
public class PaymentSuccessEvent extends PaymentEvent {

    private final Long orderId;
    private final String orderNo;
    private final Long userId;
    private final BigDecimal paidAmount;
    private final String thirdPartyOrderNo;
    private final String channelType;
    private final String channelSubType;

    public PaymentSuccessEvent(Object source, Long tenantId, Long merchantId, Long orderId, String orderNo,
                               Long userId, BigDecimal paidAmount, String thirdPartyOrderNo,
                               String channelType, String channelSubType) {
        super(source, "PAYMENT_SUCCESS", tenantId, merchantId);
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.paidAmount = paidAmount;
        this.thirdPartyOrderNo = thirdPartyOrderNo;
        this.channelType = channelType;
        this.channelSubType = channelSubType;
    }
}
