package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 商户支付规则 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantPaymentConfigDTO {

    /**
     * 排序reusable
    */
    private Boolean orderReusable;

    /**
     * 排序超时minutes
    */
    private Integer orderTimeoutMinutes;

    /**
     * Pending排序限制
    */
    private Integer pendingOrderLimit;

    /**
     * Autocancel超时排序
    */
    private Boolean autoCancelTimeoutOrder;
}

