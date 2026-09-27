package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;

/**
 * 支付宝退款响应
 *
 * @param success 成功
 * @param refundNo 退税no
 * @param tradeNo 贸易no
 * @param refundAmount Refundamount
 * @param refundStatus 退税状态
 * @param message 消息
 * @param rawResponse RAW响应
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayRefundResponse(
        boolean success,
        String refundNo,
        String tradeNo,
        BigDecimal refundAmount,
        String refundStatus,
        String message,
        String rawResponse
) {
}

