package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户钱包限额 DTO
 */
@Data
public class MerchantWalletLimitDTO {

    private BigDecimal singleRechargeLimit;

    private BigDecimal dailyRechargeLimit;

    private BigDecimal singleWithdrawLimit;

    private BigDecimal dailyWithdrawLimit;

    private BigDecimal singleTransferLimit;

    private BigDecimal dailyTransferLimit;

    private BigDecimal balanceLimit;
}
