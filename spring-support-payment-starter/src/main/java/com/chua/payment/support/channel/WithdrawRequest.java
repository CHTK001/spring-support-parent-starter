package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求
 * @author CH
 */
@Data
public class WithdrawRequest {
    /** WithdrawNO */
    private String withdrawNo;
    /** NotifyURL */
    private String notifyUrl;
    /** MerchantID */
    private Long merchantId;
    /** 用户ID */
    private Long userId;
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

