package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 转账结果
 *
 * @param success     是否成功
 * @param transferNo  转账单号
 * @param tradeNo     交易单号
 * @param amount      转账金额
 * @param status      转账状态
 * @param message     提示消息
 * @param rawResponse 渠道 RAW 响应
 * @author CH
 * @since 4.0.0
 */
public record TransferResult(
        boolean success,
        String transferNo,
        String tradeNo,
        BigDecimal amount,
        String status,
        String message,
        String rawResponse) {
}
