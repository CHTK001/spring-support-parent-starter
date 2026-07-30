package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款结果
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class RefundResult {
    private boolean success;
    private String refundNo;
    private String tradeNo;
    private String message;
    private BigDecimal refundAmount;
    private String status;
    private String rawResponse;
}
