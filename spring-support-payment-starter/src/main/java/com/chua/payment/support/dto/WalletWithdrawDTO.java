package com.chua.payment.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包提现DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletWithdrawDTO {
    private Long merchantId;
    private Long userId;
    private String withdrawNo;
    private String notifyUrl;
    private BigDecimal amount;
    private String bankAccount;
    private String bankName;
    private String accountName;
    private String operator;
    private String remark;
}
