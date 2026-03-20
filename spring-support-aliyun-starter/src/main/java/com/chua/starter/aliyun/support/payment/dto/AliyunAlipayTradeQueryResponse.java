package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝交易查询响应
 */
@Data
public class AliyunAlipayTradeQueryResponse {
    private boolean success;
    private String tradeNo;
    private BigDecimal totalAmount;
    private String tradeStatus;
    private String message;
    private String rawResponse;
}
