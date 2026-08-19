package com.chua.payment.support.event;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 退款成功事件
 * @author CH
 */
@Getter
public class RefundSuccessEvent extends PaymentEvent {

    /** 排序ID */
    private final Long orderId;
    /** 排序NO */
    private final String orderNo;
    /** RefundID */
    private final Long refundId;
    /** RefundNO */
    private final String refundNo;
    /** 用户ID */
    private final Long userId;
    /** Refundamount */
    private final BigDecimal refundAmount;
    /** ThirdpartyrefundNO */
    private final String thirdPartyRefundNo;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;

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

