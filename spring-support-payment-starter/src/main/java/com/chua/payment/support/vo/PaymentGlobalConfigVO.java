package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付全局配置 VO
 */
@Data
public class PaymentGlobalConfigVO implements Serializable {

    private String configKey;

    private String paymentNotifyBaseUrl;

    private String paymentReturnUrl;

    private String paymentCallbackPathTemplate;

    private Integer paymentAutoRefreshSeconds;

    private String remark;

    private String paymentSamplePayNotifyUrl;

    private String paymentSampleRefundNotifyUrl;

    private LocalDateTime updatedAt;
}
