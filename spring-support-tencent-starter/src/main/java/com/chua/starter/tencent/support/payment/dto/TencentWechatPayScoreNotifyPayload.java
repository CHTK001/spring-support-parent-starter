package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分服务订单回调解析结果
 * @author CH
 */
@Data
public class TencentWechatPayScoreNotifyPayload {
    /** OUT排序NO */
    private String outOrderNo;
    /** 服务排序NO */
    private String serviceOrderNo;
    /** APPID */
    private String appId;
    /** 服务ID */
    private String serviceId;
    /** OpenID */
    private String openId;
    /** 状态 */
    private String state;
    /** Finishreason */
    private String finishReason;
    private Map<String, Object> rawData;
}

