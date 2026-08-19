package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信下单请求
 * @author CH
 */
@Data
public class TencentWechatPayRequest {
    /** 排序NO */
    private String orderNo;
    /** AmountFEN */
    private Long amountFen;
    /** Currency */
    private String currency;
    /** 描述 */
    private String description;
    /** NotifyURL */
    private String notifyUrl;
    /** Expire时间 */
    private String expireTime;
    /** PayeropenID */
    private String payerOpenId;
    /** 客户端IP */
    private String clientIp;
    /** DeviceID */
    private String deviceId;
    /** Attach */
    private String attach;
    /** H5type */
    private String h5Type;
    /** APP名称 */
    private String appName;
    /** APPURL */
    private String appUrl;
}

