package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;

/**
 * 支付宝退款请求
 *
 * @param orderNo 排序NO
 * @param refundNo 退税no
 * @param refundAmount Refundamount
 * @param reason ReasonMLmlml
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayRefundRequest(
        String orderNo,
        String refundNo,
        BigDecimal refundAmount,
        String reason
) {
}

