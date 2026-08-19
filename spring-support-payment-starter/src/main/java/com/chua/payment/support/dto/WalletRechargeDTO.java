package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包充值 DTO
 * @author CH
 */
@Data
public class WalletRechargeDTO {
    /** MerchantID */
    private Long merchantId;
    /** 用户ID */
    private Long userId;
    /** RechargeNO */
    private String rechargeNo;
    /** NotifyURL */
    private String notifyUrl;
    /** Amount */
    private BigDecimal amount;
    /** Operator */
    private String operator;
    /** Remark */
    private String remark;
}

