package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

/**
* 支付宝下单响应
* @author CH
* @since 4.0.0
 */
@Data
public class AliyunAlipayPayResponse {
    /** 成功 */
    private boolean success;
    /** 请求体 */
    private String body;
    /** 贸易no */
    private String tradeNo;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

