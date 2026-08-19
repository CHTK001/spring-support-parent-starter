package com.chua.payment.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包转账DTO
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransferDTO {
    /** MerchantID */
    private Long merchantId;
    /** From用户ID */
    private Long fromUserId;
    /** TO用户ID */
    private Long toUserId;
    /** TransferNO */
    private String transferNo;
    /** NotifyURL */
    private String notifyUrl;
    /** Amount */
    private BigDecimal amount;
    /** Operator */
    private String operator;
    /** Remark */
    private String remark;
}

