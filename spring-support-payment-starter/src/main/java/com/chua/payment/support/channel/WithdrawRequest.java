package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 提现请求
 *
 * @param withdrawNo   提现单号
 * @param notifyUrl    通知回调 URL
 * @param merchantId   商户 ID
 * @param userId       用户标识
 * @param amount       提现金额
 * @param bankAccount  收款银行账号
 * @param bankName     收款银行名称
 * @param accountName  账户名称
 * @param operator     操作人
 * @param remark       备注
 * @author CH
 * @since 4.0.0
 */
public record WithdrawRequest(
        String withdrawNo,
        String notifyUrl,
        Long merchantId,
        Long userId,
        BigDecimal amount,
        String bankAccount,
        String bankName,
        String accountName,
        String operator,
        String remark) {
}
