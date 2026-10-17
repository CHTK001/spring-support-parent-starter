package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 提现结果
 *
 * @param success     是否成功
 * @param withdrawNo  提现单号
 * @param tradeNo     交易单号
 * @param amount      提现金额
 * @param status      提现状态
 * @param message     提示消息
 * @param rawResponse 渠道 RAW 响应
 * @author CH
 * @since 4.0.0
 */
public record WithdrawResult(
        boolean success,
        String withdrawNo,
        String tradeNo,
        BigDecimal amount,
        String status,
        String message,
        String rawResponse) {
}
