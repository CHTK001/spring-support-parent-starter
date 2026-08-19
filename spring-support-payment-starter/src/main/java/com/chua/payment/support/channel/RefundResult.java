package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款结果
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class RefundResult {
    /** Success */
    private boolean success;
    /** RefundNO */
    private String refundNo;
    /** TradeNO */
    private String tradeNo;
    /** 消息 */
    private String message;
    /** Refundamount */
    private BigDecimal refundAmount;
    /** 状态 */
    private String status;
    /** RAW响应 */
    private String rawResponse;
}
