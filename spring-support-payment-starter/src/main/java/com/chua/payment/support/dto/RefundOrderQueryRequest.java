package com.chua.payment.support.dto;

/**
* 退款订单分页查询请求参数对象。
*
* @param pageNum     页码
* @param pageSize    每页大小
* @param merchantId  商户标识
* @param orderNo     原订单号（可选）
* @param refundNo    退款单号（可选）
* @param status      状态（可选）
* @author CH
* @since 4.0.0.50
 */
public record RefundOrderQueryRequest(
        int pageNum,
        int pageSize,
        Long merchantId,
        String orderNo,
        String refundNo,
        String status
) {
}
