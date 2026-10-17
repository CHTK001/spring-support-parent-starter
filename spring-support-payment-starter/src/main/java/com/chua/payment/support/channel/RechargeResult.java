package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 充值结果
 *
 * @param success     是否成功
 * @param rechargeNo  充值单号
 * @param tradeNo     交易单号
 * @param amount      充值金额
 * @param status      充值状态
 * @param message     提示消息
 * @param rawResponse 渠道 RAW 响应
 * @author CH
 * @since 4.0.0
 */
public record RechargeResult(
        boolean success,
        String rechargeNo,
        String tradeNo,
        BigDecimal amount,
        String status,
        String message,
        String rawResponse) {
}
