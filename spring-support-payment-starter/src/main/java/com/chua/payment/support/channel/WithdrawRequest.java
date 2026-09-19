package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class WithdrawRequest {
    /** withdrawno */
    private String withdrawNo;
    /** 通知url */
    private String notifyUrl;
    /** merchantid */
    private Long merchantId;
    /** 用户标识 */
    private Long userId;
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

