package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分服务订单响应
 */
@Data
public class TencentWechatPayScoreResponse {
    private boolean success;
    private String outOrderNo;
    private String serviceOrderNo;
    private String state;
    private String packageInfo;
    private String message;
    private String rawResponse;
    private Map<String, Object> rawData;
}
