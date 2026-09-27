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
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 退款单号
     */
    private String refundNo;
    /**
     * 交易单号
     */
    private String tradeNo;
    /**
     * 提示消息
     */
    private String message;
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;
    /**
     * 退款状态
     */
    private String status;
    /**
     * 渠道 RAW 响应
     */
    private String rawResponse;
}
