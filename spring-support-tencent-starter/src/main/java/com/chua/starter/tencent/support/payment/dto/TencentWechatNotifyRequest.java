package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信支付回调请求
 */
@Data
public class TencentWechatNotifyRequest {
    private String serialNumber;
    private String timestamp;
    private String nonce;
    private String signature;
    private String signType;
    private String body;
}
