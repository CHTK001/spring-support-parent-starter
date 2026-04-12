package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付全局配置 DTO
 */
@Data
public class PaymentGlobalConfigDTO {

    private String paymentNotifyBaseUrl;

    private String paymentReturnUrl;

    private String paymentCallbackPathTemplate;

    private Integer paymentAutoRefreshSeconds;

    private String remark;
}
