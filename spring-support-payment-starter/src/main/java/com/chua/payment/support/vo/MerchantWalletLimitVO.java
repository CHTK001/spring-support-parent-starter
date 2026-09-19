package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商户钱包限额 VO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantWalletLimitVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * merchantid
    */
    private Long merchantId;

    /**
     * Singlerecharge限制
    */
    private BigDecimal singleRechargeLimit;

    /**
     * Dailyrecharge限制
    */
    private BigDecimal dailyRechargeLimit;

    /**
     * Singlewithdraw限制
    */
    private BigDecimal singleWithdrawLimit;

    /**
     * Dailywithdraw限制
    */
    private BigDecimal dailyWithdrawLimit;

    /**
     * Singletransfer限制
    */
    private BigDecimal singleTransferLimit;

    /**
     * Dailytransfer限制
    */
    private BigDecimal dailyTransferLimit;

    /**
     * Balance限制
    */
    private BigDecimal balanceLimit;
}
