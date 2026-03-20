package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信下单请求
 */
@Data
public class TencentWechatPayRequest {
    private String orderNo;
    private Long amountFen;
    private String currency;
    private String description;
    private String notifyUrl;
    private String expireTime;
    private String payerOpenId;
    private String clientIp;
    private String deviceId;
    private String attach;
    private String h5Type;
    private String appName;
    private String appUrl;
}
