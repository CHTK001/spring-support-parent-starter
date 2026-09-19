package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信支付回调请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatNotifyRequest {
    /** 串行数字 */
    private String serialNumber;
    /** 时间戳 */
    private String timestamp;
    /** Nonce */
    private String nonce;
    /** 签名 */
    private String signature;
    /** 标志类型 */
    private String signType;
    /** 请求体 */
    private String body;
}

