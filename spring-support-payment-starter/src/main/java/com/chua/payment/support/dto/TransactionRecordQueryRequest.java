package com.chua.payment.support.dto;

/**
 * 客户交易记录分页查询请求参数对象。
 *
 * @param pageNum           页码
 * @param pageSize          每页大小
 * @param merchantId        客户标识
 * @param orderNo           订单号（可选）
 * @param transactionType   交易类型（可选）
 * @param status            状态（可选）
 * @author CH
 * @since 4.0.0.50
 */
public record TransactionRecordQueryRequest(
        int pageNum,
        int pageSize,
        Long merchantId,
        String orderNo,
        String transactionType,
        Integer status
) {
}
