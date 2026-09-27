package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 客户钱包限额VO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantWalletLimitVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 商户ID
     */
    private Long merchantId;

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
