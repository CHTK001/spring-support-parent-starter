package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包提现DTO
 *
 * @param merchantId  商户ID
 * @param userId      用户标识
 * @param withdrawNo  提现单号
 * @param notifyUrl   通知URL
 * @param amount      金额
 * @param bankAccount 银行账号
 * @param bankName    银行名称
 * @param accountName 账户名
 * @param operator    操作人
 * @param remark      备注
 * @author CH
 * @since 4.0.0
 */
public record WalletWithdrawDTO(
        Long merchantId,
        Long userId,
        String withdrawNo,
        String notifyUrl,
        BigDecimal amount,
        String bankAccount,
        String bankName,
        String accountName,
        String operator,
        String remark) {
}
