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
    /**
     * 商户ID
     */
    private Long merchantId;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 提现单号
     */
    private String withdrawNo;
    /**
     * 通知URL
     */
    private String notifyUrl;
    /**
     * 金额
     */
    private BigDecimal amount;
    /**
     * 银行账号
     */
    private String bankAccount;
    /**
     * 银行名称
     */
    private String bankName;
    /**
     * 账户名
     */
    private String accountName;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 备注
     */
    private String remark;
}
