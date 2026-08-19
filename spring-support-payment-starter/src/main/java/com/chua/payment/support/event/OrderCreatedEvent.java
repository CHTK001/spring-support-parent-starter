package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 订单创建事件
 * @author CH
 */
@Getter
public class OrderCreatedEvent extends PaymentEvent {

    /** 排序ID */
    private final Long orderId;
    /** 排序NO */
    private final String orderNo;
    /** 用户ID */
    private final Long userId;
    /** 排序amount */
    private final BigDecimal orderAmount;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;
    /** Subject */
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

