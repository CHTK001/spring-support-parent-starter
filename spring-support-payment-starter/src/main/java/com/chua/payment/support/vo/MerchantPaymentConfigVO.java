package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户支付配置VO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantPaymentConfigVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 订单可复用
     */
    private Boolean orderReusable;

    /**
     * 订单超时分钟数
     */
    private Integer orderTimeoutMinutes;

    /**
     * 待支付订单限制
     */
    private Integer pendingOrderLimit;

    /**
     * 自动取消超时订单
     */
    private Boolean autoCancelTimeoutOrder;
}
