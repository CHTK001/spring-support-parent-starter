package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝退款请求
 */
@Data
public class AliyunAlipayRefundRequest {
    private String orderNo;
    private String refundNo;
    private BigDecimal refundAmount;
    private String reason;
}
