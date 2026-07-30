package com.chua.payment.support.service;

import com.chua.payment.support.entity.PaymentNotifyLog;

/**
 * 支付回调日志服务
 */
public interface PaymentNotifyLogService {

    PaymentNotifyLog start(String notifyType,
                           Long channelId,
                           String requestHeaders,
                           String requestBody);

    void finish(Long logId,
                Integer signVerified,
                String channelType,
                String channelSubType,
                String processStatus,
                String processResult,
                Long relatedOrderId,
                Long relatedRefundId);
}
