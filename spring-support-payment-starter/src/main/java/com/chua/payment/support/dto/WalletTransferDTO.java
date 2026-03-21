package com.chua.payment.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包转账DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransferDTO {
    private Long merchantId;
    private Long fromUserId;
    private Long toUserId;
    private String transferNo;
    private BigDecimal amount;
    private String operator;
    private String remark;
}
