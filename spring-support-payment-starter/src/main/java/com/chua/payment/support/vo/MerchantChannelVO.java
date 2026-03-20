package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商户支付方式视图对象
 */
@Data
public class MerchantChannelVO implements Serializable {

    private Long id;

    private Long merchantId;

    private String merchantName;

    private String channelType;

    private String channelSubType;

    private String channelName;

    private String appId;

    private String merchantNo;

    private Boolean apiKeyConfigured;

    private Boolean privateKeyConfigured;

    private Boolean publicKeyConfigured;

    private Boolean certConfigured;

    private Integer sandboxMode;

    private String notifyUrl;

    private String returnUrl;

    private String onboardingStatus;

    private String onboardingStatusDesc;

    private String onboardingLink;

    private Integer status;

    private String statusDesc;

    private String extConfig;

    private String providerSpi;

    private String guideTitle;

    private String guideUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
