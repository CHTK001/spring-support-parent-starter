package com.chua.payment.support.dto;

/**
 * 客户支付配置DTO
 *
 * @param orderReusable           订单可复用
 * @param orderTimeoutMinutes     订单超时分钟数
 * @param pendingOrderLimit       待支付订单限制
 * @param autoCancelTimeoutOrder  自动取消超时订单
 * @author CH
 * @since 4.0.0
 */
public record MerchantPaymentConfigDTO(
        Boolean orderReusable,
        Integer orderTimeoutMinutes,
        Integer pendingOrderLimit,
        Boolean autoCancelTimeoutOrder) {
}
