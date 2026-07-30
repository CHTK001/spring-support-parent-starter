package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 商户支付规则 DTO
 */
@Data
public class MerchantPaymentConfigDTO {

    private Boolean orderReusable;

    private Integer orderTimeoutMinutes;

    private Integer pendingOrderLimit;

    private Boolean autoCancelTimeoutOrder;
}
