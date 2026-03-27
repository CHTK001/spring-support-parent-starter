package com.chua.payment.support.vo;

import lombok.Data;

/**
 * 回调策略审计视图
 */
@Data
public class PaymentCallbackAuditVO {
    private String callbackType;
    private String callbackName;
    private String recommendedPattern;
    private String scopedIdentifier;
    private Boolean strictScoped;
    private String effectivePriority;
    private String notes;
}
