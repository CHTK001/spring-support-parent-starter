package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商户支付规则 VO
 * @author CH
 */
@Data
public class MerchantPaymentConfigVO implements Serializable {

    /** MerchantID */
    private Long merchantId;

    /** 排序reusable */
    private Boolean orderReusable;

    /** 排序超时minutes */
    private Integer orderTimeoutMinutes;

    /** Pending排序限制 */
    private Integer pendingOrderLimit;

    /** Autocancel超时排序 */
    private Boolean autoCancelTimeoutOrder;
}

