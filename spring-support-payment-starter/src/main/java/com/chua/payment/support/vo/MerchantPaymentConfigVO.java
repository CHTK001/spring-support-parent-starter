package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商户支付规则 VO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantPaymentConfigVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** merchantid */
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
