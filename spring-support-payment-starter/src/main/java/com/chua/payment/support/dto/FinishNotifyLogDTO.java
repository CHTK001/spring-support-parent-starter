package com.chua.payment.support.dto;

/**
 * 完成支付回调日志处理请求参数对象。
 *
 * @param logId          日志ID
 * @param signVerified   签名是否校验通过
 * @param channelType    渠道类型
 * @param channelSubType 渠道子类型
 * @param processStatus  处理状态
 * @param processResult  处理结果
 * @param relatedOrderId 关联支付订单ID
 * @param relatedRefundId 关联退款单ID
 * @author CH
 * @since 4.0.0.50
 */
public record FinishNotifyLogDTO(
        Long logId,
        Integer signVerified,
        String channelType,
        String channelSubType,
        String processStatus,
        String processResult,
        Long relatedOrderId,
        Long relatedRefundId
) {
}
