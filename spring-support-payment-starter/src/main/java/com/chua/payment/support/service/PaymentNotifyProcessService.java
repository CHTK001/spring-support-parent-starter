package com.chua.payment.support.service;

import com.chua.payment.support.entity.PaymentNotifyLog;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付回调处理服务
 */
public interface PaymentNotifyProcessService {

    /**
     * 记录回调日志
     */
    PaymentNotifyLog logNotify(String notifyType,
                               Long merchantId,
                               Long channelId,
                               String channelType,
                               String channelSubType,
                               String orderNo,
                               String refundNo,
                               HttpServletRequest request,
                               String body);

    /**
     * 处理支付回调
     */
    void processPaymentNotify(PaymentNotifyLog log);

    /**
     * 处理退款回调
     */
    void processRefundNotify(PaymentNotifyLog log);

    /**
     * 标记处理成功
     */
    void markSuccess(Long logId, String result);

    /**
     * 标记处理失败并记录异常
     */
    void markFailedAndRecordError(Long logId, String errorType, String errorMessage, String errorStack);

    /**
     * 重试处理失败的回调
     */
    void retryFailedNotify(Long errorId);
}
