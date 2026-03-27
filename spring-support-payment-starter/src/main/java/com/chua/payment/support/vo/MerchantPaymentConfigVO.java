package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商户支付规则 VO
 */
@Data
public class MerchantPaymentConfigVO implements Serializable {

    private Long merchantId;

    private Boolean orderReusable;

    private Integer orderTimeoutMinutes;

    private Integer pendingOrderLimit;

    private Boolean autoCancelTimeoutOrder;
}
