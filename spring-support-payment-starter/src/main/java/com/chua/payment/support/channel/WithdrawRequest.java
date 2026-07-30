package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求
 */
@Data
public class WithdrawRequest {
    private String withdrawNo;
    private String notifyUrl;
    private Long merchantId;
    private Long userId;
    private BigDecimal amount;
    private String bankAccount;
    private String bankName;
    private String accountName;
    private String operator;
    private String remark;
}
