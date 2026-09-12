package com.chua.payment.support.dto;

/**
* 分页查询支付回调错误日志请求参数对象。
*
* @param pageNum      页码
* @param pageSize     每页大小
* @param notifyType   通知类型
* @param status       错误状态
* @param orderNo      订单号
* @param refundNo     退款单号
* @author CH
* @since 4.0.0.50
 */
public record PageNotifyErrorsRequest(
        int pageNum,
        int pageSize,
        String notifyType,
        String status,
        String orderNo,
        String refundNo
) {
}
