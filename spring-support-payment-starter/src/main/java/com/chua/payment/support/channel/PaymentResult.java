package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付结果
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class PaymentResult {
    /**
     * 成功
    */
    private boolean success;
    /**
     * 贸易no
    */
    private String tradeNo;
    /**
     * 消息
    */
    private String message;
    /**
     * PAYURL
    */
    private String payUrl;
    /**
     * Launch类型
    */
    private String launchType;
    /**
     * formhtml
    */
    private String formHtml;
    /**
     * 请求体
    */
    private String body;
    private Map<String, Object> sdkParams; // sdk参数
    /**
     * Paidamount
    */
    private BigDecimal paidAmount;
    /**
     * 状态
    */
    private String status;
    /**
     * RAW响应
    */
    private String rawResponse;
}
