package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 订单创建事件
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
public class OrderCreatedEvent extends PaymentEvent {

    /**
     * 订单标识
     */
    private final Long orderId;
    /**
     * 订单号
     */
    private final String orderNo;
    /**
     * 用户标识
     */
    private final Long userId;
    /**
     * 订单金额
     */
    private final BigDecimal orderAmount;
    /**
     * 渠道类型
     */
    private final String channelType;
    /**
     * 渠道子类型
     */
    private final String channelSubType;
    /**
     * 主题
     */
    private final String subject;

    /**
     * 创建订单创建事件实例
     *
     * @param source 来源
     * @param tenantId 租户标识
     * @param merchantId 商户标识
     * @param orderId 订单标识
     * @param orderNo 订单号
     * @param userId 用户标识
     * @param orderAmount 订单金额
     * @param channelType 渠道类型
     * @param channelSubType 渠道子类型
     * @param subject 主题
     */
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
