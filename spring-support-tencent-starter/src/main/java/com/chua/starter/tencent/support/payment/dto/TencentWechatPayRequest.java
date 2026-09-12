package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信下单请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatPayRequest {
    /** 排序NO */
    private String orderNo;
    /** amountfen */
    private Long amountFen;
    /** 货币 */
    private String currency;
    /** 描述 */
    private String description;
    /** 通知url */
    private String notifyUrl;
    /** Expire时间 */
    private String expireTime;
    /** payeropenid */
    private String payerOpenId;
    /** 客户端IP */
    private String clientIp;
    /** deviceid */
    private String deviceId;
    /** Attach */
    private String attach;
    /** H5类型 */
    private String h5Type;
    /** APP名称 */
    private String appName;
    /** APPURL */
    private String appUrl;
}

