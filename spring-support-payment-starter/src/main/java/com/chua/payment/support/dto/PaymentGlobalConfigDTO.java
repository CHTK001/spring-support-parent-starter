package com.chua.payment.support.dto;

/**
 * 支付全局配置 DTO
 *
 * @param paymentNotifyBaseUrl         支付通知基础URL
 * @param paymentReturnUrl             支付返回值URL
 * @param paymentCallbackPathTemplate  支付回调路径模板
 * @param paymentAutoRefreshSeconds    支付自动刷新秒数
 * @param couponDeductWithdrawable     优惠券抵扣是否可提现
 * @param remark                       备注
 * @author CH
 * @since 4.0.0
 */
public record PaymentGlobalConfigDTO(
        String paymentNotifyBaseUrl,
        String paymentReturnUrl,
        String paymentCallbackPathTemplate,
        Integer paymentAutoRefreshSeconds,
        Boolean couponDeductWithdrawable,
        String remark) {
}
