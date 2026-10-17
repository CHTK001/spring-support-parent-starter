package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 退款请求
 *
 * @param orderNo      订单号
 * @param userId       用户标识
 * @param tradeNo      交易单号
 * @param refundNo     退款单号
 * @param refundAmount 退款金额
 * @param totalAmount  订单总金额
 * @param reason       退款原因
 * @param notifyUrl    通知回调 URL
 * @since 2026-03-18
 * @author CH
 */
public record RefundRequest(
        String orderNo,
        Long userId,
        String tradeNo,
        String refundNo,
        BigDecimal refundAmount,
        BigDecimal totalAmount,
        String reason,
        String notifyUrl) {
}
