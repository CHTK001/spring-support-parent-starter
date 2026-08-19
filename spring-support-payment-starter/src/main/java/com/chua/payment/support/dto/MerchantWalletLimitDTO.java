package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户钱包限额 DTO
 * @author CH
 */
@Data
public class MerchantWalletLimitDTO {

    /** Singlerecharge限制 */
    private BigDecimal singleRechargeLimit;

    /** Dailyrecharge限制 */
    private BigDecimal dailyRechargeLimit;

    /** Singlewithdraw限制 */
    private BigDecimal singleWithdrawLimit;

    /** Dailywithdraw限制 */
    private BigDecimal dailyWithdrawLimit;

    /** Singletransfer限制 */
    private BigDecimal singleTransferLimit;

    /** Dailytransfer限制 */
    private BigDecimal dailyTransferLimit;

    /** Balance限制 */
    private BigDecimal balanceLimit;
}

