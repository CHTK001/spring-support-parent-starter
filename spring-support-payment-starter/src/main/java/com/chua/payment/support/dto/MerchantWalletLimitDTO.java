package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户钱包限额DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantWalletLimitDTO {

    /**
     * 单次充值限额
     */
    private BigDecimal singleRechargeLimit;

    /**
     * 每日充值限额
     */
    private BigDecimal dailyRechargeLimit;

    /**
     * 单次提现限额
     */
    private BigDecimal singleWithdrawLimit;

    /**
     * 每日提现限额
     */
    private BigDecimal dailyWithdrawLimit;

    /**
     * 单次转账限额
     */
    private BigDecimal singleTransferLimit;

    /**
     * 每日转账限额
     */
    private BigDecimal dailyTransferLimit;

    /**
     * 余额限额
     */
    private BigDecimal balanceLimit;
}
