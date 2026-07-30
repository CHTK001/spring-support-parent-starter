package com.chua.payment.support.vo;

import lombok.Data;

/**
 * 订单号策略视图
 */
@Data
public class PaymentOrderNumberStrategyVO {
    private String businessType;
    private String fieldName;
    private String generationRule;
    private String callerOverrideField;
    private String idempotentRule;
    private String notes;
}
