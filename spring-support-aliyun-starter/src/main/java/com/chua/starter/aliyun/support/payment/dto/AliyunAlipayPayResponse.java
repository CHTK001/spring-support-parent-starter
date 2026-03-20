package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

/**
 * 支付宝下单响应
 */
@Data
public class AliyunAlipayPayResponse {
    private boolean success;
    private String body;
    private String tradeNo;
    private String message;
    private String rawResponse;
}
