package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝退款响应
 * @author CH
 */
@Data
public class AliyunAlipayRefundResponse {
    /** Success */
    private boolean success;
    /** RefundNO */
    private String refundNo;
    /** TradeNO */
    private String tradeNo;
    /** Refundamount */
    private BigDecimal refundAmount;
    /** Refund状态 */
    private String refundStatus;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

