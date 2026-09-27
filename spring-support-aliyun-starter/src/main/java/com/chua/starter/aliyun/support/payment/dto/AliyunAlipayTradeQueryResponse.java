package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;

/**
 * 支付宝交易查询响应
 *
 * @param success 成功
 * @param tradeNo 贸易no
 * @param totalAmount 总数amount
 * @param tradeStatus 贸易状态
 * @param message 消息
 * @param rawResponse RAW响应
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayTradeQueryResponse(
        boolean success,
        String tradeNo,
        BigDecimal totalAmount,
        String tradeStatus,
        String message,
        String rawResponse
) {
}

