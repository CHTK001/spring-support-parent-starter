package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信退款响应
 *
 * @param success 成功
 * @param refundNo 退税no
 * @param refundId 退税标识
 * @param refundAmountFen refundamountfen
 * @param refundStatus 退税状态
 * @param message 消息
 * @param rawResponse RAW响应
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatRefundResponse(
        boolean success,
        String refundNo,
        String refundId,
        Long refundAmountFen,
        String refundStatus,
        String message,
        String rawResponse
) {
}

