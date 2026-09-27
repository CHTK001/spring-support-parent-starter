package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 客户支付配置DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantPaymentConfigDTO {

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
