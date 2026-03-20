package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包充值 DTO
 */
@Data
public class WalletRechargeDTO {
    private Long merchantId;
    private Long userId;
    private String rechargeNo;
    private BigDecimal amount;
    private String operator;
    private String remark;
}
