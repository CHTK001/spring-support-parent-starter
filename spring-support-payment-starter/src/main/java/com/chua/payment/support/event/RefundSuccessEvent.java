package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 退款成功事件
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
public class RefundSuccessEvent extends PaymentEvent {

    /**
     * 订单标识
     */
    private final Long orderId;
    /**
     * 订单号
     */
    private final String orderNo;
    /**
     * 退款标识
     */
    private final Long refundId;
    /**
     * 退款单号
     */
    private final String refundNo;
    /**
     * 用户标识
     */
    private final Long userId;
    /**
     * 退款金额
     */
    private final BigDecimal refundAmount;
    /**
     * 第三方退款单号
     */
    private final String thirdPartyRefundNo;
    /**
     * 渠道类型
     */
    private final String channelType;
    /**
     * 渠道子类型
     */
    private final String channelSubType;

    /**
     * 创建退款成功事件实例
     *
     * @param source 来源
     * @param tenantId 租户标识
     * @param merchantId 商户标识
     * @param orderId 订单标识
     * @param orderNo 订单号
     * @param refundId 退款标识
     * @param refundNo 退款单号
     * @param userId 用户标识
     * @param refundAmount 退款金额
     * @param thirdPartyRefundNo 第三方退款单号
     * @param channelType 渠道类型
     * @param channelSubType 渠道子类型
     */
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
