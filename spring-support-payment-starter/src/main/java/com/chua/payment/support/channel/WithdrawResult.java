package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现结果
 */
@Data
public class WithdrawResult {
    private boolean success;
    private String withdrawNo;
    private String tradeNo;
    private BigDecimal amount;
    private String status;
    private String message;
    private String rawResponse;
}
