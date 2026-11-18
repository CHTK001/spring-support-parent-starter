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

    /**
     * 开始记录一条支付回调通知日志。
     *
     * @param notifyType     通知类型
     * @param channelId      支付渠道 ID
     * @param requestHeaders 回调请求头
     * @param requestBody    回调请求体
     * @return 新建的通知日志记录
     */
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
