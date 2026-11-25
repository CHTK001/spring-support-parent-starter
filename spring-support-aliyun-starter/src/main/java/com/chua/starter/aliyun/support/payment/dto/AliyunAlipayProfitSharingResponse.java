package com.chua.starter.aliyun.support.payment.dto;

/**
 * 支付宝分账响应
 *
 * @param success   是否成功
 * @param settleNo  支付宝分账单号
 * @param tradeNo   支付宝交易号
 * @param message   消息
 * @param rawBody   原始响应体
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayProfitSharingResponse(
        boolean success,
        String settleNo,
        String tradeNo,
        String message,
        String rawBody
) {
}
