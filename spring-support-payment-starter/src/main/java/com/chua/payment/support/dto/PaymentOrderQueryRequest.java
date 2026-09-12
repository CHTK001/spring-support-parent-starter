package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
* 钱包支付查询请求参数对象。
*
* @param userId     用户标识
* @param page       页码
* @param size       每页大小
* @param orderNo    订单号
* @param status     状态
* @author CH
* @since 4.0.0.50
 */
public record PaymentOrderQueryRequest(
        Long userId,
        int page,
        int size,
        String orderNo,
        String status
) {
}
