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
     * 是否成功
     */
    private boolean success;
    /**
     * 交易单号
     */
    private String tradeNo;
    /**
     * 提示消息
     */
    private String message;
    /**
     * 支付跳转地址
     */
    private String payUrl;
    /**
     * 拉起方式
     */
    private String launchType;
    /**
     * 表单 HTML
     */
    private String formHtml;
    /**
     * 请求体
     */
    private String body;
    private Map<String, Object> sdkParams; // SDK 调起参数
    /**
     * 实付金额
     */
    private BigDecimal paidAmount;
    /**
     * 支付状态
     */
    private String status;
    /**
     * 渠道 RAW 响应
     */
    private String rawResponse;
}
