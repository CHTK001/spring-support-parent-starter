package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账结果
 */
@Data
public class TransferResult {
    private boolean success;
    private String transferNo;
    private String tradeNo;
    private BigDecimal amount;
    private String status;
    private String message;
    private String rawResponse;
}
