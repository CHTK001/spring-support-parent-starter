package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付结果
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class PaymentResult {
    private boolean success;
    private String tradeNo;
    private String message;
    private String payUrl;
    private String launchType;
    private String formHtml;
    private String body;
    private Map<String, Object> sdkParams;
    private BigDecimal paidAmount;
    private String status;
    private String rawResponse;
}
