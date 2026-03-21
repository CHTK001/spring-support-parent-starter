package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值结果
 */
@Data
public class RechargeResult {
    private boolean success;
    private String rechargeNo;
    private String tradeNo;
    private BigDecimal amount;
    private String status;
    private String message;
    private String rawResponse;
}
