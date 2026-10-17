package com.chua.payment.support.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付全局配置 VO
 *
 * @param configKey                     配置键
 * @param paymentNotifyBaseUrl          支付通知基础URL
 * @param paymentReturnUrl              支付返回值URL
 * @param paymentCallbackPathTemplate   支付回调路径模板
 * @param paymentAutoRefreshSeconds     支付自动刷新秒数
 * @param remark                        备注
 * @param paymentSamplePayNotifyUrl     支付示例支付通知URL
 * @param paymentSampleRefundNotifyUrl  支付示例退款通知URL
 * @param couponDeductWithdrawable      优惠券抵扣是否可提现
 * @param updatedAt                     更新时间
 * @author CH
 * @since 4.0.0
 */
public record PaymentGlobalConfigVO(
        String configKey,
        String paymentNotifyBaseUrl,
        String paymentReturnUrl,
        String paymentCallbackPathTemplate,
        Integer paymentAutoRefreshSeconds,
        String remark,
        String paymentSamplePayNotifyUrl,
        String paymentSampleRefundNotifyUrl,
        Boolean couponDeductWithdrawable,
        LocalDateTime updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
