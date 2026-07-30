package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付通知解析结果
 */
@Data
public class AliyunAlipayPayNotifyPayload {
    private String orderNo;
    private String tradeNo;
    private String appId;
    private String sellerId;
    private BigDecimal totalAmount;
    private String tradeStatus;
    private Map<String, String> rawParams;
}
