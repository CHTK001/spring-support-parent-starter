package com.chua.payment.support.dto;

import com.chua.payment.support.entity.PaymentOrder;

import java.math.BigDecimal;

/**
* 创建退款单请求参数对象。
*
* @param order          关联的支付订单
* @param refundNo       退款单号
* @param refundAmount   退款金额
* @param reason         退款原因
* @param operator       操作人
* @param requestPayload 请求报文
* @author CH
* @since 4.0.0.50
 */
public record CreateRefundOrderDTO(
        PaymentOrder order,
        String refundNo,
        BigDecimal refundAmount,
        String reason,
        String operator,
        String requestPayload
) {
}
