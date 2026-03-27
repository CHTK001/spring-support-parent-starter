package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商户钱包限额 VO
 */
@Data
public class MerchantWalletLimitVO implements Serializable {

    private Long merchantId;

    private BigDecimal singleRechargeLimit;

    private BigDecimal dailyRechargeLimit;

    private BigDecimal singleWithdrawLimit;

    private BigDecimal dailyWithdrawLimit;

    private BigDecimal singleTransferLimit;

    private BigDecimal dailyTransferLimit;

    private BigDecimal balanceLimit;
}
