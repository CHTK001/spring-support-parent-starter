package com.chua.payment.support.vo;

import java.io.Serializable;

/**
 * 客户支付配置VO
 *
 * @param merchantId             商户ID
 * @param orderReusable          订单可复用
 * @param orderTimeoutMinutes    订单超时分钟数
 * @param pendingOrderLimit      待支付订单限制
 * @param autoCancelTimeoutOrder 自动取消超时订单
 * @author CH
 * @since 4.0.0
 */
public record MerchantPaymentConfigVO(
        Long merchantId,
        Boolean orderReusable,
        Integer orderTimeoutMinutes,
        Integer pendingOrderLimit,
        Boolean autoCancelTimeoutOrder) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
