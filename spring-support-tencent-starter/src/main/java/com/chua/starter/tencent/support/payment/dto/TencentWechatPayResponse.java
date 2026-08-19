package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信下单响应
 * @author CH
 */
@Data
public class TencentWechatPayResponse {
    /** Success */
    private boolean success;
    /** PAYURL */
    private String payUrl;
    private Map<String, Object> sdkParams;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

