package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 退款结果
 *
 * @param success      是否成功
 * @param refundNo     退款单号
 * @param tradeNo      交易单号
 * @param message      提示消息
 * @param refundAmount 退款金额
 * @param status       退款状态
 * @param rawResponse  渠道 RAW 响应
 * @since 2026-03-18
 * @author CH
 */
public record RefundResult(
        boolean success,
        String refundNo,
        String tradeNo,
        String message,
        BigDecimal refundAmount,
        String status,
        String rawResponse) {
}
