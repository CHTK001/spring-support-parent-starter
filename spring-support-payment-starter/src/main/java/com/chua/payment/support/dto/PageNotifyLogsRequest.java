package com.chua.payment.support.dto;

/**
 * 分页查询支付回调日志请求参数对象。
 *
 * @param pageNum       页码
 * @param pageSize      每页大小
 * @param notifyType    通知类型
 * @param processStatus 处理状态
 * @param orderNo       订单号
 * @param refundNo      退款单号
 * @author CH
 * @since 4.0.0.50
 */
public record PageNotifyLogsRequest(
        int pageNum,
        int pageSize,
        String notifyType,
        String processStatus,
        String orderNo,
        String refundNo
) {
}
