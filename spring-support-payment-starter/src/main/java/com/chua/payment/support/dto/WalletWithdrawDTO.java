package com.chua.payment.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包提现DTO
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletWithdrawDTO {
    /** merchantid */
    private Long merchantId;
    /** 用户标识 */
    private Long userId;
    /** withdrawno */
    private String withdrawNo;
    /** 通知url */
    private String notifyUrl;
    /** Amount */
    private BigDecimal amount;
    /** Bankaccount */
    private String bankAccount;
    /** Bank名称 */
    private String bankName;
    /** 账户名称 */
    private String accountName;
    /** 操作符 */
    private String operator;
    /** 评论 */
    private String remark;
}

