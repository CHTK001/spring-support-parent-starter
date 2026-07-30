package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信下单响应
 */
@Data
public class TencentWechatPayResponse {
    private boolean success;
    private String payUrl;
    private Map<String, Object> sdkParams;
    private String message;
    private String rawResponse;
}
