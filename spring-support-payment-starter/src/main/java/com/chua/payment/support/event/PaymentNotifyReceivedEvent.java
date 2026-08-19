package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调接收事件
 * @author CH
 */
@Getter
public class PaymentNotifyReceivedEvent extends PaymentEvent {

    /** Notify日志ID */
    private final Long notifyLogId;
    /** Notify类型 */
    private final String notifyType;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;
    /** 排序NO */
    private final String orderNo;
    /** RefundNO */
    private final String refundNo;
    /** Signverified */
    private final Boolean signVerified;

    /**
     * 创建 PaymentNotifyReceivedEvent 实例
     * @param source source
     * @param tenantId tenantId
     * @param merchantId merchantId
     * @param notifyLogId notifyLogId
     * @param notifyType notifyType
     * @param channelType channelType
     * @param channelSubType channelSubType
     * @param orderNo orderNo
     * @param refundNo refundNo
     * @param signVerified signVerified
     */
    public PaymentNotifyReceivedEvent(Object source, Long tenantId, Long merchantId, Long notifyLogId,
                                      String notifyType, String channelType, String channelSubType,
                                      String orderNo, String refundNo, Boolean signVerified) {
        super(source, "PAYMENT_NOTIFY_RECEIVED", tenantId, merchantId);
        this.notifyLogId = notifyLogId;
        this.notifyType = notifyType;
        this.channelType = channelType;
        this.channelSubType = channelSubType;
        this.orderNo = orderNo;
        this.refundNo = refundNo;
        this.signVerified = signVerified;
    }
}

