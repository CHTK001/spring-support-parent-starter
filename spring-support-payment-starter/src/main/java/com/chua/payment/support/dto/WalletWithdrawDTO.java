package com.chua.payment.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包提现DTO
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletWithdrawDTO {
    /** MerchantID */
    private Long merchantId;
    /** 用户ID */
    private Long userId;
    /** WithdrawNO */
    private String withdrawNo;
    /** NotifyURL */
    private String notifyUrl;
    /** Amount */
    private BigDecimal amount;
    /** Bankaccount */
    private String bankAccount;
    /** Bank名称 */
    private String bankName;
    /** Account名称 */
    private String accountName;
    /** Operator */
    private String operator;
    /** Remark */
    private String remark;
}

