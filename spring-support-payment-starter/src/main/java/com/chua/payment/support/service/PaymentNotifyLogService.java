package com.chua.payment.support.service;

import com.chua.payment.support.dto.FinishNotifyLogDTO;
import com.chua.payment.support.entity.PaymentNotifyLog;

/**
 * 支付回调日志服务接口，定义通知日志的创建与完成记录契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentNotifyLogService {

    PaymentNotifyLog start(String notifyType,
                           Long channelId,
                           String requestHeaders,
                           String requestBody);

    /**
     * 标记通知日志处理完成。
     * @param dto dto
     */
    void finish(FinishNotifyLogDTO dto);
}
