package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 退款成功事件
 * @author CH
 * @since 4.0.0
 */
@Getter
public class RefundSuccessEvent extends PaymentEvent {

    /** 排序标识 */
    private final Long orderId;
    /** 排序NO */
    private final String orderNo;
    /** 退税标识 */
    private final Long refundId;
    /** 退税no */
    private final String refundNo;
    /** 用户标识 */
    private final Long userId;
    /** Refundamount */
    private final BigDecimal refundAmount;
    /** thirdpartyrefundno */
    private final String thirdPartyRefundNo;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;

    /**
      * 创建 退税成功事件 实例
     * @param source 源
     * @param tenantId tenantid
     * @param merchantId merchantid
     * @param orderId 订单标识
     * @param orderNo 订单no
     * @param refundId 退税标识
     * @param refundNo 退税no
     * @param userId 用户标识
     * @param refundAmount 退税amount
     * @param thirdPartyRefundNo thirdparty退税no
     * @param channelType 通道类型
     * @param channelSubType 通道sub类型
     */
    public RefundSuccessEvent(Object source, Long tenantId, Long merchantId, Long orderId, String orderNo,
                              Long refundId, String refundNo, Long userId, BigDecimal refundAmount,
                              String thirdPartyRefundNo, String channelType, String channelSubType) {
        /**
         * 父。
         * @param source 源
         * @param "REFUND_SUCCESS" "退税_成功"
         * @param tenantId tenantid
         * @param merchantId merchantid
         */
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

