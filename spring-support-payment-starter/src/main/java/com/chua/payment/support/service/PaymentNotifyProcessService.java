package com.chua.payment.support.service;

import com.chua.payment.support.dto.LogNotifyDTO;
import com.chua.payment.support.entity.PaymentNotifyLog;

/**
* 支付回调处理服务接口，定义通知日志记录、分发与状态标记契约。
*
* @author CH
* @since 4.0.0.50
 */
public interface PaymentNotifyProcessService {

    /**
    * 记录回调日志
    * @param dto dto
    * @return 日志通知的结果
     */
    PaymentNotifyLog logNotify(LogNotifyDTO dto);

    /**
    * 处理支付回调
    * @param log 日志
     */
    void processPaymentNotify(PaymentNotifyLog log);

    /**
    * 处理退款回调
    * @param log 日志
     */
    void processRefundNotify(PaymentNotifyLog log);

    /**
    * 标记处理成功
    * @param logId 日志标识
    * @param result 结果
     */
    void markSuccess(Long logId, String result);

    /**
    * 标记处理失败并记录异常
    * @param logId 日志标识
    * @param errorType 错误类型
    * @param errorMessage 错误消息
    * @param errorStack 错误stack
     */
    void markFailedAndRecordError(Long logId, String errorType, String errorMessage, String errorStack);

    /**
    * 重试处理失败的回调
    * @param errorId 错误标识
     */
    void retryFailedNotify(Long errorId);
}

