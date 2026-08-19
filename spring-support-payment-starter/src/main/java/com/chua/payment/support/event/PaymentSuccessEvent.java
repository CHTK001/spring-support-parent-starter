package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 支付成功事件
 * @author CH
 */
@Getter
public class PaymentSuccessEvent extends PaymentEvent {

    /** 排序ID */
    private final Long orderId;
    /** 排序NO */
    private final String orderNo;
    /** 用户ID */
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
     * 创建 PaymentSuccessEvent 实例
     * @param source source
     * @param tenantId tenantId
     * @param merchantId merchantId
     * @param orderId orderId
     * @param orderNo orderNo
     * @param userId userId
     * @param paidAmount paidAmount
     * @param thirdPartyOrderNo thirdPartyOrderNo
     * @param channelType channelType
     * @param channelSubType channelSubType
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

