package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
* 微信下单响应
* @author CH
* @since 4.0.0
 */
@Data
public class TencentWechatPayResponse {
    /** 成功 */
    private boolean success;
    /** PAYURL */
    private String payUrl;
    private Map<String, Object> sdkParams; // sdk参数
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

