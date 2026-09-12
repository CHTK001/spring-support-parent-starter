package com.chua.payment.support.dto;

/**
* 用户交易记录查询请求参数对象。
*
* @param userId            用户标识
* @param pageNum           页码
* @param pageSize          每页大小
* @param orderNo           订单号（可选）
* @param transactionType   交易类型（可选）
* @param status            状态（可选）
* @author CH
* @since 4.0.0.50
 */
public record UserTransactionQueryRequest(
        Long userId,
        int pageNum,
        int pageSize,
        String orderNo,
        String transactionType,
        Integer status
) {
}
