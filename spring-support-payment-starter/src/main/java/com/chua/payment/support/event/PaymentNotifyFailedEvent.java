package com.chua.payment.support.event;

import lombok.Getter;

/**
 * 支付回调处理失败事件
 * @author CH
 */
@Getter
public class PaymentNotifyFailedEvent extends PaymentEvent {

    /** Notify日志ID */
    private final Long notifyLogId;
    /** Notify错误ID */
    private final Long notifyErrorId;
    /** Notify类型 */
    private final String notifyType;
    /** 排序NO */
    private final String orderNo;
    /** RefundNO */
    private final String refundNo;
    /** 错误类型 */
    private final String errorType;
    /** 错误消息 */
    private final String errorMessage;

    /**
     * 创建 PaymentNotifyFailedEvent 实例
     * @param source source
     * @param tenantId tenantId
     * @param merchantId merchantId
     * @param notifyLogId notifyLogId
     * @param notifyErrorId notifyErrorId
     * @param notifyType notifyType
     * @param orderNo orderNo
     * @param refundNo refundNo
     * @param errorType errorType
     * @param errorMessage errorMessage
     */
    public PaymentNotifyFailedEvent(Object source, Long tenantId, Long merchantId, Long notifyLogId,
                                    Long notifyErrorId, String notifyType, String orderNo, String refundNo,
                                    String errorType, String errorMessage) {
        super(source, "PAYMENT_NOTIFY_FAILED", tenantId, merchantId);
        this.notifyLogId = notifyLogId;
        this.notifyErrorId = notifyErrorId;
        this.notifyType = notifyType;
        this.orderNo = orderNo;
        this.refundNo = refundNo;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }
}

