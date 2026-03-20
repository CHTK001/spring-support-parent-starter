package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝退款响应
 */
@Data
public class AliyunAlipayRefundResponse {
    private boolean success;
    private String refundNo;
    private String tradeNo;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String message;
    private String rawResponse;
}
