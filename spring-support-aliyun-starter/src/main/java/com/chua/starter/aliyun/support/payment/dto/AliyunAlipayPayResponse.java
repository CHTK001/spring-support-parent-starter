package com.chua.starter.aliyun.support.payment.dto;

/**
 * 支付宝下单响应
 *
 * @param success 成功
 * @param body 请求体
 * @param tradeNo 贸易no
 * @param message 消息
 * @param rawResponse RAW响应
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayPayResponse(
        boolean success,
        String body,
        String tradeNo,
        String message,
        String rawResponse
) {
}

