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
    /** 成功 */
    private boolean success;
    /** 退税no */
    private String refundNo;
    /** 贸易no */
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
