package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 客户钱包限额DTO
 *
 * @param singleRechargeLimit 单次充值限额
 * @param dailyRechargeLimit  每日充值限额
 * @param singleWithdrawLimit 单次提现限额
 * @param dailyWithdrawLimit  每日提现限额
 * @param singleTransferLimit 单次转账限额
 * @param dailyTransferLimit  每日转账限额
 * @param balanceLimit        余额限额
 * @author CH
 * @since 4.0.0
 */
public record MerchantWalletLimitDTO(
        BigDecimal singleRechargeLimit,
        BigDecimal dailyRechargeLimit,
        BigDecimal singleWithdrawLimit,
        BigDecimal dailyWithdrawLimit,
        BigDecimal singleTransferLimit,
        BigDecimal dailyTransferLimit,
        BigDecimal balanceLimit) {
}
