package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 支付成功事件
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
public class PaymentSuccessEvent extends PaymentEvent {

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
     * 实付金额
     */
    private final BigDecimal paidAmount;
    /**
     * 第三方订单号
     */
    private final String thirdPartyOrderNo;
    /**
     * 渠道类型
     */
    private final String channelType;
    /**
     * 渠道子类型
     */
    private final String channelSubType;

    /**
     * 创建支付成功事件实例
     *
     * @param source 来源
     * @param tenantId 租户标识
     * @param merchantId 商户标识
     * @param orderId 订单标识
     * @param orderNo 订单号
     * @param userId 用户标识
     * @param paidAmount 实付金额
     * @param thirdPartyOrderNo 第三方订单号
     * @param channelType 渠道类型
     * @param channelSubType 渠道子类型
     */
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
