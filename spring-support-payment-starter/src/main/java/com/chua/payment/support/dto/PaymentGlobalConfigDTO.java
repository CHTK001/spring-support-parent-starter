package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付全局配置 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentGlobalConfigDTO {

    /**
     * 支付通知基础URL
     */
    private String paymentNotifyBaseUrl;

    /**
     * 支付返回值URL
     */
    private String paymentReturnUrl;

    /**
     * 支付回调路径模板
     */
    private String paymentCallbackPathTemplate;

    /**
     * 支付自动刷新秒数
     */
    private Integer paymentAutoRefreshSeconds;

    /**
     * 优惠券抵扣是否可提现
     */
    private Boolean couponDeductWithdrawable;

    /**
     * 备注
     */
    private String remark;
}
