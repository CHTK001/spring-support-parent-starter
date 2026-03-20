package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝下单请求
 */
@Data
public class AliyunAlipayPayRequest {
    private String orderNo;
    private BigDecimal totalAmount;
    private String subject;
    private String body;
    private String notifyUrl;
    private String returnUrl;
    private String productCode;
}
