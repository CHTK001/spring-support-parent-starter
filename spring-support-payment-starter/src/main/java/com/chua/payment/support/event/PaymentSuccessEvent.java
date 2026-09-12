package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 支付成功事件
 * @author CH
 * @since 4.0.0
 */
@Getter
public class PaymentSuccessEvent extends PaymentEvent {

    /** 排序标识 */
    private final Long orderId;
    /** 排序NO */
    private final String orderNo;
    /** 用户标识 */
    private final Long userId;
    /** Paidamount */
    private final BigDecimal paidAmount;
    /** Thirdparty排序NO */
    private final String thirdPartyOrderNo;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;

    /**
      * 创建 薪酬成功事件 实例
     * @param source 源
     * @param tenantId tenantid
     * @param merchantId merchantid
     * @param orderId 订单标识
     * @param orderNo 订单no
     * @param userId 用户标识
     * @param paidAmount paidamount
     * @param thirdPartyOrderNo thirdparty订单no
     * @param channelType 通道类型
     * @param channelSubType 通道sub类型
     */
    public PaymentSuccessEvent(Object source, Long tenantId, Long merchantId, Long orderId, String orderNo,
                               Long userId, BigDecimal paidAmount, String thirdPartyOrderNo,
                               String channelType, String channelSubType) {
        /**
         * 父。
         * @param source 源
         * @param "PAYMENT_SUCCESS" "薪酬_成功"
         * @param tenantId tenantid
         * @param merchantId merchantid
         */
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

