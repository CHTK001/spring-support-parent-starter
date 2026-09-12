package com.chua.payment.support.dto;

/**
* 用户订单查询请求参数对象。
*
* @param userId    用户标识
* @param page      页码
* @param size      每页大小
* @param orderNo   订单号（可选）
* @param status    订单状态（可选）
* @author CH
* @since 4.0.0.50
 */
public record UserOrderQueryRequest(
        Long userId,
        int page,
        int size,
        String orderNo,
        String status
) {
}
