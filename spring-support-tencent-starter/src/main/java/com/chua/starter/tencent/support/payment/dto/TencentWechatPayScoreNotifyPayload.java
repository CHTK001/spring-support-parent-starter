package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分服务订单回调解析结果
 */
@Data
public class TencentWechatPayScoreNotifyPayload {
    private String outOrderNo;
    private String serviceOrderNo;
    private String appId;
    private String serviceId;
    private String openId;
    private String state;
    private String finishReason;
    private Map<String, Object> rawData;
}
