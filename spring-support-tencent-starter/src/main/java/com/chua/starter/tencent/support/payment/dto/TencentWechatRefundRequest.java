package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信退款请求
 *
 * @param orderNo 排序NO
 * @param tradeNo 贸易no
 * @param refundNo 退税no
 * @param refundAmountFen refundamountfen
 * @param totalAmountFen 总数amountfen
 * @param reason ReasonMLmlml
 * @param notifyUrl 通知url
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatRefundRequest(
        String orderNo,
        String tradeNo,
        String refundNo,
        Long refundAmountFen,
        Long totalAmountFen,
        String reason,
        String notifyUrl
) {
}

