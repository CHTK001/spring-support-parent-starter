package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

/**
 * 支付宝下单响应
 * @author CH
 */
@Data
public class AliyunAlipayPayResponse {
    /** Success */
    private boolean success;
    /** 请求体 */
    private String body;
    /** TradeNO */
    private String tradeNo;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

