package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调接收事件
 * @author CH
 * @since 4.0.0
 */
@Getter
public class PaymentNotifyReceivedEvent extends PaymentEvent {

    /** 通知日志标识 */
    private final Long notifyLogId;
    /** 通知类型 */
    private final String notifyType;
    /** 通道类型 */
    private final String channelType;
    /** 通道SUB类型 */
    private final String channelSubType;
    /** 排序NO */
    private final String orderNo;
    /** 退税no */
    private final String refundNo;
    /** Signverified */
    private final Boolean signVerified;

    /**
     * 创建 薪酬通知接收事件 实例
     * @param source 源
     * @param tenantId tenantid
     * @param merchantId merchantid
     * @param notifyLogId 通知日志标识
     * @param notifyType 通知类型
     * @param channelType 通道类型
     * @param channelSubType 通道sub类型
     * @param orderNo 订单no
     * @param refundNo 退税no
     * @param signVerified 标志verified
     */
    public PaymentNotifyReceivedEvent(Object source, Long tenantId, Long merchantId, Long notifyLogId,
                                      String notifyType, String channelType, String channelSubType,
                                      String orderNo, String refundNo, Boolean signVerified) {
        /**
         * 父。
         * @param source 源
         * @param "PAYMENT_NOTIFY_RECEIVED" "payment通知received"
         * @param tenantId tenantid
         * @param merchantId merchantid
         */
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

