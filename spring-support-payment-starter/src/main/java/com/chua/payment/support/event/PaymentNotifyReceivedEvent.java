package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调接收事件
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
public class PaymentNotifyReceivedEvent extends PaymentEvent {

    /**
     * 通知日志标识
     */
    private final Long notifyLogId;
    /**
     * 通知类型
     */
    private final String notifyType;
    /**
     * 渠道类型
     */
    private final String channelType;
    /**
     * 渠道子类型
     */
    private final String channelSubType;
    /**
     * 订单号
     */
    private final String orderNo;
    /**
     * 退款单号
     */
    private final String refundNo;
    /**
     * 签名是否验签通过
     */
    private final Boolean signVerified;

    /**
     * 创建支付通知接收事件实例
     *
     * @param source 来源
     * @param tenantId 租户标识
     * @param merchantId 商户标识
     * @param notifyLogId 通知日志标识
     * @param notifyType 通知类型
     * @param channelType 渠道类型
     * @param channelSubType 渠道子类型
     * @param orderNo 订单号
     * @param refundNo 退款单号
     * @param signVerified 验签是否通过
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
