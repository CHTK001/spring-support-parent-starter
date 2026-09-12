package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 订单创建事件
 * @author CH
 * @since 4.0.0
 */
@Getter
public class OrderCreatedEvent extends PaymentEvent {

    /** 排序标识 */
    private final Long orderId;
    /** 排序NO */
    private final String orderNo;
    /** 用户标识 */
    private final Long userId;
    /** 排序amount */
    private final BigDecimal orderAmount;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;
    /** 主题 */
    private final String subject;

    /**
      * 创建 订单创建事件 实例
     * @param source 源
     * @param tenantId tenantid
     * @param merchantId merchantid
     * @param orderId 订单标识
     * @param orderNo 订单no
     * @param userId 用户标识
     * @param orderAmount 订单amount
     * @param channelType 通道类型
     * @param channelSubType 通道sub类型
     * @param subject 主题
     */
    public OrderCreatedEvent(Object source, Long tenantId, Long merchantId, Long orderId, String orderNo,
                             Long userId, BigDecimal orderAmount, String channelType, String channelSubType, String subject) {
        /**
         * 父。
         * @param source 源
         * @param "ORDER_CREATED" "订单_创建"
         * @param tenantId tenantid
         * @param merchantId merchantid
         */
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

