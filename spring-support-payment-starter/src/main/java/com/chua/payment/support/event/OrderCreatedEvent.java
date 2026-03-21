package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 订单创建事件
 */
@Getter
public class OrderCreatedEvent extends PaymentEvent {

    private final Long orderId;
    private final String orderNo;
    private final Long userId;
    private final BigDecimal orderAmount;
    private final String channelType;
    private final String channelSubType;
    private final String subject;

    public OrderCreatedEvent(Object source, Long tenantId, Long merchantId, Long orderId, String orderNo,
                             Long userId, BigDecimal orderAmount, String channelType, String channelSubType, String subject) {
        super(source, "ORDER_CREATED", tenantId, merchantId);
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.orderAmount = orderAmount;
        this.channelType = channelType;
        this.channelSubType = channelSubType;
        this.subject = subject;
    }
}
